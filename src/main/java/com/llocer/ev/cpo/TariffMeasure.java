package com.llocer.ev.cpo;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.llocer.common.Tuple2;
import com.llocer.ev.ocpi.msgs22.OcpiPriceComponent;
import com.llocer.ev.ocpi.msgs22.OcpiPriceComponent.Type;
import com.llocer.ev.ocpi.msgs22.OcpiTariff;

public class TariffMeasure {
	public final long t; // interval in milliseconds from TariffIntervals.start
	
	public Long totalReservationTime = null;
	public Long totalParkingTime = null;
	public Long totalChargingTime = null;
	public Double totalEnergy = null;
	
	public Double current = null;
	public Double power = null;

	private Map<OcpiPriceComponent.Type,Tuple2<OcpiTariff,OcpiPriceComponent>> components = null;
	
	public TariffMeasure( long t ) {
		this.t = t;
	}
	
	public double getEnergy() {
		return this.totalEnergy;
	}
	
	public void setEnergy( double v ) {
		this.totalEnergy = v;
	}
	
	public Double getCurrent() {
		return this.current;
	}
	
	public void setCurrent( double v ) {
		this.current = v;
	}
	
	public Double getPower() {
		return this.power;
	}
	
	public void setPower( double v ) {
		this.power = v;
	}
	
	public Tuple2<OcpiTariff, OcpiPriceComponent> getComponent( OcpiPriceComponent.Type pct ) {
		if( components == null ) return null;
		return components.get( pct );
	}
	
	public void setComponent( Tuple2<OcpiTariff, OcpiPriceComponent> tariffAndPriceComponent ) {
		if( components == null ) {
			components = new EnumMap<OcpiPriceComponent.Type,Tuple2<OcpiTariff,OcpiPriceComponent>>(OcpiPriceComponent.Type.class);
		}
		components.put( tariffAndPriceComponent.f2.getType(), tariffAndPriceComponent );
	}
	
	public void putAllComponents( TariffMeasure orig ) {
		if( orig.components == null ) return;
		
		if( components == null ) {
			components = new EnumMap<OcpiPriceComponent.Type,Tuple2<OcpiTariff,OcpiPriceComponent>>(OcpiPriceComponent.Type.class);
		}
		components.putAll( orig.components );
	}

	public Set<Entry<Type, Tuple2<OcpiTariff, OcpiPriceComponent>>> componentsSet() {
		if( components == null ) return Collections.emptySet();
		return components.entrySet();
	}

	TariffMeasure dup() {
		TariffMeasure res = new TariffMeasure( this.t );
		res.totalReservationTime = this.totalReservationTime;
		res.totalParkingTime = this.totalParkingTime;
		res.totalChargingTime = this.totalChargingTime;
		res.totalEnergy = this.totalEnergy;
		res.current = this.current;
		res.power = this.power;
		res.putAllComponents( this );
		return res;
	}
	
	public static TariffMeasure zero() {
		TariffMeasure res = new TariffMeasure( 0 );
		res.totalReservationTime = 0L;
		res.totalParkingTime = 0L;
		res.totalChargingTime = 0L;
		res.totalEnergy = 0.0;
		res.current = 0.0;
		res.power = 0.0;
		return res;
	}
	
	void interpolation( TariffMeasure vi, TariffMeasure vf ) {
		double percent = (vf.t == vi.t ? 0.0 : ((double)t-vi.t)/(vf.t-vi.t) );

		// interpolation
		if( totalReservationTime == null && vf.totalReservationTime != null ) {
			totalReservationTime = (long) (vi.totalReservationTime+percent*(vf.totalReservationTime-vi.totalReservationTime));
		}
		
		// interpolation
		if( totalParkingTime == null && vf.totalParkingTime != null ) {
			totalParkingTime = (long) (vi.totalParkingTime+percent*(vf.totalParkingTime-vi.totalParkingTime));
		}

		// interpolation
		if( totalChargingTime == null && vf.totalChargingTime != null ) {
			totalChargingTime = (long) (vi.totalChargingTime+percent*(vf.totalChargingTime-vi.totalChargingTime));
		}
		
		// interpolation
		if( totalEnergy == null && vf.totalEnergy != null ) {
			totalEnergy = vi.totalEnergy+percent*(vf.totalEnergy-vi.totalEnergy);
		}
		
		// copy previous
		if( current == null && vi.current != null ) {
			current = vi.current;
		}
		
		// copy previous
		if( power == null && vi.power != null ) {
			power = vi.power;
		}
	}
	
	public static TariffMeasure interpolation( TariffMeasure vi, long t, TariffMeasure vf ) {
		TariffMeasure res = new TariffMeasure(t);
		res.interpolation( vi, vf );
		res.putAllComponents( vi );
		return res;
	}
	
	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append( "{" );
		
		res.append( "t=" );
		res.append( t/1000.0 );

		res.append( ", energy=" );
		res.append( this.totalEnergy );

		res.append( ", current=" );
		res.append( this.current );

		res.append( ", power=" );
		res.append( this.power );

		res.append( ", reservationTime=" );
		res.append( this.totalReservationTime == null ? null : this.totalReservationTime/1000.0 );

		res.append( ", parkingTime=" );
		res.append( this.totalParkingTime == null ? null : this.totalParkingTime/1000.0 );

		res.append( ", chargingTime=" );
		res.append( this.totalChargingTime == null? null : this.totalChargingTime/1000.0 );

		
		res.append( ", { " );
		for( Entry<Type, Tuple2<OcpiTariff, OcpiPriceComponent>> e : componentsSet() ) {
			res.append( e.getKey() );
			res.append( " " );
		}
		res.append( "}" );
		
		res.append( "}" );
		return res.toString();
	}
}

