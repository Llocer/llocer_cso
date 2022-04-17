package com.llocer.ev.cpo;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Function;

import com.llocer.common.Log;
import com.llocer.common.Tuple2;
import com.llocer.ev.ocpi.msgs22.OcpiPriceComponent;
import com.llocer.ev.ocpi.msgs22.OcpiTariff;

public class TariffMeasures implements Iterable<TariffMeasure> {
	
	private LinkedList<TariffMeasure> data;
	
	public TariffMeasures() {
		this.data = new LinkedList<TariffMeasure>();
	}
	
	public void add( TariffMeasure tm ) {
		data.addLast( tm );
	}
	
	@Override
	public Iterator<TariffMeasure> iterator() {
		return data.iterator();
	}
	
	public void dump( String label ) {
		for( TariffMeasure tm : data ) {
			Log.debug( "TariffIntervals.dump: %s => %s", label, tm );
		}
		Log.debug( "TariffIntervals.dump: %s -----", label );
	}
	
	public TariffMeasures dup() {
		TariffMeasures res = new TariffMeasures();
		for( TariffMeasure tm : data ) {
			res.add( tm.dup() );
		}
		return res;
	}
	
	public void joinMeasure( TariffMeasures measure ) {
		
		Iterator<TariffMeasure> it1 = data.iterator();
		Iterator<TariffMeasure> it2 = measure.data.iterator();
		
		TariffMeasure tm1 = Sugar.next( it1 );
		TariffMeasure tm2 = Sugar.next( it2 );
		if( tm1 == null || tm2 == null) return;
		
		TariffMeasure last1 = null;
		TariffMeasure last2 = null;
		
		TariffMeasures res = new TariffMeasures();
		
		while( tm1 != null ) {
			if( tm2 == null ) {
				tm1.interpolation( last2, last2 );
				res.add( tm1 );

//				last1 = tm1;
				tm1 = Sugar.next( it1 );
				
			} else if( tm1.t < tm2.t ) {
				tm1.interpolation( last2, tm2 );
				res.add( tm1 );

				last1 = tm1;
				tm1 = Sugar.next( it1 );
				
			} else if( tm1.t == tm2.t ) {
				tm1.interpolation( tm2, tm2 );
				res.add( tm1 );

				last1 = tm1;
				tm1 = Sugar.next( it1 );
				last2 = tm2;
				tm2 = Sugar.next( it2 );
				
			} else { // tm1.t > tm2.t
				tm2.interpolation( last1, tm1 );
				res.add( tm2 );

				last2 = tm2;
				tm2 = Sugar.next( it2 );
				
			}
		}
		
//		res.dump( "and::res");
		data = res.data;
	}
	
	public void assign( Tuple2<OcpiTariff, OcpiPriceComponent> tariffAndPriceComponent, TariffFlags assigned) {
//		dump( "p");
//		assigned.dump( "a" );
		
		Iterator<TariffMeasure> pIt = data.iterator();
		Iterator<TariffFlag> aIt = assigned.iterator();
		
		TariffMeasures res = new TariffMeasures(); 
				
		TariffMeasure p = Sugar.next( pIt );
		TariffFlag a = Sugar.next( aIt );
		if( a==null || p==null ) return;
		
		TariffMeasure last_p = null;
		TariffFlag last_a = new TariffFlag(0L, false );
		
		while( p != null  ) {
			if( a == null ) { 
				if( last_a.ok ) {
					p.setComponent(tariffAndPriceComponent);
				}
				res.add( p );				
				
//				last_p = p;
				p = Sugar.next( pIt );
				
			} else if( p.t < a.t ) { 
				TariffMeasure n = p.dup();
				if( last_a.ok ) {
					n.setComponent(tariffAndPriceComponent);
				}
				res.add( n );				
				
				last_p = p;
				p = Sugar.next( pIt );
				
			} else if( p.t == a.t ) {
				TariffMeasure n = p.dup();
				if( a.ok ) {
					n.setComponent(tariffAndPriceComponent);
				}
				res.add( n );	
				
				last_p = p;
				p = Sugar.next( pIt );

				last_a = a;
				a = Sugar.next( aIt );
				
			} else { // a.t < p.t 
				if( last_p.getComponent(tariffAndPriceComponent.f2.getType()) == null ) {
					// [a...] < p
					TariffMeasure n = TariffMeasure.interpolation( last_p, a.t, p );
					if( a.ok ) {
						n.setComponent(tariffAndPriceComponent);
					}
					res.add( n );
				}

				last_a = a;
				a = Sugar.next( aIt );
				
			}
		}
		
		data = res.data;
		res.data = null;
//		dump( "p'");
	}
	
	public TariffFlags threshold( boolean max, double threshold, Function<TariffMeasure,Double> f ) {
		TariffFlags res = new TariffFlags();
		
		boolean last_ok = false;
		TariffMeasure last = null;
		
		for( TariffMeasure tm : data ) {
			double v = f.apply(tm);
			boolean ok = ( max ? v < threshold : v >= threshold );
		
			if( last == null ) {
				// first
				res.add( tm.t, ok );
				
			} else if( last_ok != ok ){
				double percent = (threshold-f.apply(last))/(v-f.apply(last));
				long t = (long)( last.t+percent*(tm.t-last.t) );
				res.add( t, ok );
				
			}
			
			last = tm;
			last_ok = ok;
		}

		return res;
	}

//	public TariffIntervals timeType( Function<TariffMark,Long> f ) {
//		TariffIntervals res = new TariffIntervals();
//
//		long last_duration = 0L;
//		TariffMark last = null;
//		
//		for( TariffMark tm : data ) {
//			long duration = f.apply(tm);
//		
//			if( last != null ) {
//				last.ok = (last_duration != duration);
//			}
//			
//			last = tm.dup();
//			res.add( last );
//				
//			last_duration = duration;
//		}
//
//		return res;
//	}
	
//	private TariffIntervals duration() {
//
//		TariffIntervals res = new TariffIntervals( startInstant, startValue );
//		
//		double duration = 0;
//		long last_timestamp = 0;
//		for( TariffMark tm : this.data ) {
//			if( tm.ok ) {
//				duration += tm.t-last_timestamp;
//			}
//			res.addLast( tm.ok, tm.t, duration );
//			last_timestamp = tm.t;;
//		}
//
//		return res;
//	}
//	
//	public TariffIntervals valueThreshold( boolean max, double threshold ) {
//		TariffIntervals tmp = threshold( max, threshold );
//		return and( tmp );
//	}
//	
//	public TariffIntervals durationThreshold( boolean max, long threshold ) {
//		TariffIntervals tmp = duration().threshold( max, threshold );
//		return and( tmp );
//	}
//	
//	public long sumTimes() {
//		long res = 0;
//		long lastTimestamp = 0;
//		for( TariffMark tm : data ) {
//			if( tm.ok ) {
//				res += ( tm.t-lastTimestamp );
//			}
//			lastTimestamp = tm.t;
//		}
//		return res;
//	}
//	
//	public double sumValues() {
//		double res = 0.0;
//		double lastValue = 0.0;
//		for( TariffMark tm : data ) {
//			if( tm.ok ) {
//				res += ( tm.value-lastValue );
//			}
//			lastValue = tm.value;
//		}
//		return res;
//	}
//
//	public Instant getStartTime() {
//		TariffMark last = null;
//		for( TariffMark tm : data ) {
//			if( tm.ok ) {
//				if( last == null ) return this.startInstant;
//				return this.startInstant.plusMillis( tm.t );
//			}
//			last = tm;
//		}
//		return null;
//	}

}
