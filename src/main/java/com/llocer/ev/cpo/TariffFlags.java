package com.llocer.ev.cpo;

import java.util.Iterator;
import java.util.LinkedList;

import com.llocer.common.Log;

class TariffFlag {
	public final long t; // interval in milliseconds from start time
	public final boolean ok; // true <-> valid forward 	
	
	public TariffFlag( long t, boolean ok ) {
		this.t = t;
		this.ok = ok; 
	}
	
	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append( "{" );
		
		res.append( "t=" );
		res.append( t/1000.0 );

		res.append( ", ok=" );
		res.append( this.ok );
		
		res.append( "}" );
		return res.toString();
	}
}

public class TariffFlags implements Iterable<TariffFlag> {
	private LinkedList<TariffFlag> data;
	
	public TariffFlags() {
		this.data = new LinkedList<TariffFlag>();
	}
	
	void add( TariffFlag tm ) {
		data.addLast( tm );
	}
	
	public void add( long t, boolean ok ) {
		TariffFlag tf = new TariffFlag(t, ok);
		data.addLast( tf );
	}
	
	@Override
	public Iterator<TariffFlag> iterator() {
		return data.iterator();
	}
	
	public boolean isNone() {
		for( TariffFlag tm : data ) {
			if( tm.ok ) return false;
		}
		
		return true;
	}
	
	public void dump( String label ) {
		for( TariffFlag tm : data ) {
			Log.debug( "TariffIntervals.dump: %s => %s", label, tm );
		}
		Log.debug( "TariffIntervals.dump: %s -----", label );
	}
	
	static TariffFlags allInterval( long duration) {
		TariffFlags res = new TariffFlags();

		res.add( 0, true );
		res.add( duration, false );

		return res;
	}
	

	public void and( TariffFlags tms2 ) {
//		tms1.dump( "and::tms1");
//		tms2.dump( "and::tms2");
		
		Iterator<TariffFlag> it1 = data.iterator();
		Iterator<TariffFlag> it2 = tms2.data.iterator();
		
		TariffFlag last1 = new TariffFlag(0, false); 
		TariffFlag last2 = new TariffFlag(0, false); // tm2 and last2 can not be both null
		
		TariffFlag tm1 = Sugar.next( it1 ); 
		TariffFlag tm2 = Sugar.next( it2 ); 
		if( tm1 == null || tm2 == null) return;
		
		TariffFlags res = new TariffFlags();
		
		while( tm1 != null ) {
//			if( tm1 == null ) { 
//				if( last1.ok ) {
//					res.add( tm2 );
//				}
//
//				last2 = tm2;
//				tm2 = Sugar.next( it2 );
				
			if ( tm2 == null ) { 
				if( last2.ok ) {
					res.add( tm1 );
				}
				
//				last1 = tm1;
				tm1 = Sugar.next( it1 );
				
			} else if( tm1.t < tm2.t ) {
				res.add( tm1.t, tm1.ok && last2.ok );
				last1 = tm1;
				tm1 = Sugar.next( it1 );
				
			} else if( tm1.t == tm2.t ) {
				res.add( tm1.t, tm1.ok && tm2.ok );
				last1 = tm1;
				tm1 = Sugar.next( it1 );
				last2 = tm2;
				tm2 = Sugar.next( it2 );
				
			} else { // tm1.t > tm2.t
				res.add( tm2.t, last1.ok && tm2.ok );
				last2 = tm2;
				tm2 = Sugar.next( it2 );
				
			}
		}
		
//		res.dump( "and::res");
		data = res.data;
	}

}
