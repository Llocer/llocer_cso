package com.llocer.ev.cpo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import com.llocer.common.Log;
import com.llocer.common.Tuple2;
import com.llocer.ev.csms.Transaction;
import com.llocer.ev.ocpi.modules.OcpiLocationsReceiverModule;
import com.llocer.ev.ocpi.msgs22.AuthMethod;
import com.llocer.ev.ocpi.msgs22.ChargingPreferencesResponse;
import com.llocer.ev.ocpi.msgs22.OcpiAuthorizationInfo;
import com.llocer.ev.ocpi.msgs22.OcpiCdr;
import com.llocer.ev.ocpi.msgs22.OcpiCdrToken;
import com.llocer.ev.ocpi.msgs22.OcpiChargingPeriod;
import com.llocer.ev.ocpi.msgs22.OcpiChargingPreferences;
import com.llocer.ev.ocpi.msgs22.OcpiConnector;
import com.llocer.ev.ocpi.msgs22.OcpiDimension;
import com.llocer.ev.ocpi.msgs22.OcpiDimension.CdrDimensionType;
import com.llocer.ev.ocpi.msgs22.OcpiEvse;
import com.llocer.ev.ocpi.msgs22.OcpiLocation;
import com.llocer.ev.ocpi.msgs22.OcpiPriceComponent;
import com.llocer.ev.ocpi.msgs22.OcpiPriceComponent.Type;
import com.llocer.ev.ocpi.msgs22.OcpiRestrictions;
import com.llocer.ev.ocpi.msgs22.OcpiSession;
import com.llocer.ev.ocpi.msgs22.OcpiSession.Status;
import com.llocer.ev.ocpi.msgs22.OcpiStartSession;
import com.llocer.ev.ocpi.msgs22.OcpiTariff;
import com.llocer.ev.ocpi.msgs22.OcpiTariffElement;
import com.llocer.ev.ocpi.msgs22.OcpiToken;
import com.llocer.ev.ocpi.server.OcpiAgentId;
import com.llocer.ev.ocpi.server.OcpiResult;
import com.llocer.ev.ocpp.msgs20.MeasurandEnum;
import com.llocer.ev.ocpp.msgs20.OcppEVSE;
import com.llocer.ev.ocpp.msgs20.OcppIdToken;
import com.llocer.ev.ocpp.msgs20.OcppIdTokenInfo;
import com.llocer.ev.ocpp.msgs20.OcppRequestStartTransactionRequest;
import com.llocer.ev.ocpp.msgs20.OcppTransactionEventRequest;
import com.llocer.ev.ocpp.msgs20.OcppTransactionEventResponse;

public class CpoTransaction extends Transaction {

	public OcpiAgentId cpoId;
	OcpiAgentId eMSP = null;
	
	private OcpiLocation location = null;
	private OcpiEvse evse = null;
	
	Integer ocppReservationId = null;
	String ocpiReservationId = null;

	CPO cpo;
	OcpiSession session;
	OcpiChargingPreferences chargingPreferences;
	
	private long tariffStart = 0L;
	private TariffMeasures tariffPeriods = null; // all interval with energy measures
	private OcpiCdr cdr;
	
//	CpoTransaction( CPO cpo, ChargingStation cs ) {
//		super( cs );
//		this.cpo = cpo;
//	}
//	
//	void setCpo( CPO cpo, ChargingStation cs ) {
//		this.cpoId = cpo.config.id;
//		this.cpo = cpo;
//		this.setCS( cs );
//	}
	
	@Override
	public void store() {
		this.cpo.updateTransaction( this );
	}
	
	@Override
	public void remove() {
		this.cpo.removeTransaction( this );
	}

	private OcpiCdrToken token2cdrToken( OcpiToken token ) {
		OcpiCdrToken cdrToken = new OcpiCdrToken();
		cdrToken.setCountryCode( token.getCountryCode() );
		cdrToken.setPartyId( token.getPartyId() );
		cdrToken.setUid( token.getUid() );
		cdrToken.setType( token.getType() );
		cdrToken.setContractId( token.getContractId() );
		return cdrToken;
	}
	
	@Override
	public OcppTransactionEventResponse onEvent( OcppTransactionEventRequest event ) throws Exception {
		OcppTransactionEventResponse res = super.onEvent(event);
		
		OcppIdToken idToken = lastEvent.getIdToken();
		if( idToken != null ) {
			Tuple2<OcpiAuthorizationInfo, AuthMethod> auth2 = cpo.authorize( idToken.getType(), idToken.getIdToken() );
			OcpiAuthorizationInfo authorizationInfo = auth2.f1;
			
			OcppIdTokenInfo idTokenInfo = new OcppIdTokenInfo();
			idTokenInfo.setStatus( OcpiTypeTools.toAuthorizationStatusEnum( authorizationInfo.getAllowed() ));
			setIdTokenInfo( idTokenInfo );
			
			OcpiToken token = authorizationInfo.getToken();
			if( token != null ) {
				session.setCdrToken( token2cdrToken(token) );
				
				this.eMSP = new OcpiAgentId( token.getCountryCode(), token.getPartyId() );
			}
			
			session.setAuthReference( authorizationInfo.getAuthorizationReference() );
			session.setAuthMethod( auth2.f2 );
		}

		OcppEVSE ocppEvse = lastEvent.getEvse();
		if( ocppEvse != null ) {
			Tuple2<OcpiLocation, OcpiEvse> t = cpo.getEvse( this.cs, ocppEvse.getId() );
			this.location = t.f1;			
			this.evse = t.f2;
			session.setEvseUid( this.evse.getEvseId() );
			
			Integer connectorIdx = ocppEvse.getConnectorId();
			if( connectorIdx != null ) {
				OcpiConnector ocpiConnector = OcpiLocationsReceiverModule.getConnectorByOcppIdx( this.evse, connectorIdx );
				session.setConnectorId( ocpiConnector.getId() );
			}
		}

		switch( lastEvent.getEventType() ) {
		case STARTED: {
			session.setStartDatetime( startEvent.getTimestamp());
			session.setLocationId( location.getId() );
			session.setStatus( Status.PENDING );
			
			Integer remoteStartId = event.getTransactionInfo().getRemoteStartId();
			if( remoteStartId != null ) {
				storeStartSessionCommandData(remoteStartId);
			}
			break;
		}
			
		case UPDATED: {
			break;
		}
			
		case ENDED: {
			doFinish();
			return res;
		
		}}
		
		if( isAuthorized() && isEvPresent() ) {
			session.setStatus( Status.ACTIVE );
		} else {
			session.setStatus( Status.PENDING );				
		}
		
		List<OcpiTariff> validTariffs = cpo.getApplicableTariffs( this );  
		fillCDR( validTariffs );
		
		session.setLastUpdated( lastEvent.getTimestamp() );
		cpo.updateTransaction(this);
		
		return res;
	}

	void doFinish() {
		Instant end = ( lastEvent == null ? Instant.now() : lastEvent.getTimestamp() );
		session.setStatus( Status.COMPLETED );
		session.setEndDatetime( end );
		session.setLastUpdated( end );
		
		List<OcpiTariff> validTariffs = cpo.getApplicableTariffs( this );  
		fillCDR( validTariffs );
//		Log.dump( "CDR=%s", cdr.toString().replaceAll(",", "\n" ) );
		cpo.addCDR( cdr );
		
		cpo.updateTransaction(this);
	}

	void doAbort() {
		Instant end = Instant.now();
		session.setStatus( Status.INVALID );
		session.setEndDatetime( end );
		session.setLastUpdated( end );
		
		cpo.updateTransaction(this);
	}

	
	void storeStartSessionCommandData(int remoteStartId) {
		// Transaction started by a remote command
		Tuple2<OcpiStartSession, OcppRequestStartTransactionRequest> remoteStartData = cpo.remoteStartByOcppId.get(remoteStartId);
		cpo.remoteStartByOcppId.set(remoteStartId,null);
		
		session.setCdrToken( token2cdrToken( remoteStartData.f1.getToken() ));
		session.setAuthReference( remoteStartData.f1.getAuthorizationReference() );
		session.setAuthMethod( AuthMethod.COMMAND );
	}

	

	/*****************************************************************************
	 * Tariffication
	 */

	private void initChargingTimes() {

		long reservationDuration = 0L;
		long chargingDuration = 0L;
		long parkingDuration = 0L;
		long previousT = 0L;
		Boolean charging = null; // null: reservation, false: parking, true: charging

		this.tariffStart = this.session.getStartDatetime().toEpochMilli();
		
		Iterator<OcppTransactionEventRequest> eventsIt = eventsIterator();
		while( eventsIt.hasNext() ) {
			OcppTransactionEventRequest event =  eventsIt.next();
			
			long t = event.getTimestamp().toEpochMilli()-tariffStart;
			
			// charging state until now
			if( charging == null ) {
				// reservation
				reservationDuration += t-previousT;
				
			} else if ( charging ) {
				// charging
				chargingDuration += t-previousT;
				
			} else { // charging == false
				// parking
				parkingDuration += t-previousT;
				
			}
			
			previousT = t;
			
			TariffMeasure tm = new TariffMeasure(t);
			tm.totalReservationTime = reservationDuration;
			tm.totalChargingTime = chargingDuration;
			tm.totalParkingTime = parkingDuration;
			
			if( event.getTransactionInfo() != null && event.getTransactionInfo().getChargingState() != null ) {
				// charging state from now
				switch( event.getTransactionInfo().getChargingState() ) { 
				case CHARGING:
				case SUSPENDED_EV:
					charging = true;
					break;

				case EV_CONNECTED:
				case IDLE:
				case SUSPENDED_EVSE:
					// parking
					charging = false;
					break;

				}
			}
			
			if( !charging ) {
				tm.current = 0.0;
				tm.power = 0.0;
			}
			
			tariffPeriods.add( tm );
		}
	}

	private TariffMeasures collectMeasures( MeasurandEnum measurand, boolean accumulative, BiConsumer<TariffMeasure, Double> setter ) {
		TariffMeasures res = new TariffMeasures();

		TariffMeasure tm = null;
		double vStart = 0;
		double v = 0.0;
		Iterator<Tuple2<Instant, Double>> sampledValueIt = samplesIterator( measurand );
		while( sampledValueIt.hasNext() ) {
			Tuple2<Instant,Double> sample = sampledValueIt.next();
//			Log.debug( "Tariffication.initMeasures: sample=%s", sample );

			long t = sample.f1.toEpochMilli()-tariffStart;
			
			if( tm == null && accumulative ) {
				// first energy sample
				vStart = sample.f2;
			}
			
			v = sample.f2-vStart;

			tm = new TariffMeasure( t );
			setter.accept( tm, v );
			res.add( tm );
		}

		return res;
	}
	
	private TariffFlags checkRestrictions( OcpiRestrictions restrictions ) {
//		TariffFlags res = TariffFlags.allInterval( this.session.getEndDatetime().toEpochMilli()-tariffStart );
		TariffFlags res = TariffFlags.allInterval( this.lastEvent.getTimestamp().toEpochMilli()-tariffStart );

		if( restrictions == null ) return res; 
		
		if( restrictions.getReservation() == null ) {
			if( this.startEvent == null ) return null; // no parking nor charging time
			
			long tReservationEnd = this.startEvent.getTimestamp().toEpochMilli()-this.tariffStart;
			if( tReservationEnd != 0L ) {
				// there are some reservation time, exclude it
				TariffFlags valid = new TariffFlags();
				valid.add( 0L, false );
				valid.add( tReservationEnd, true );
				res.and( valid );
			}

		} else {
			switch( restrictions.getReservation() ) {
			case RESERVATION:
				if( this.startEvent == null ) return null; // reservation expired, not applicable

				long tReservationEnd = this.startEvent.getTimestamp().toEpochMilli()-this.tariffStart;
				if( tReservationEnd == 0L ) return null; // no reservation time

				// restrict to reservation time
				TariffFlags valid = new TariffFlags();
				valid.add( 0L, true );
				valid.add( tReservationEnd, false );
				res.and( valid );
				break;
				
			case RESERVATION_EXPIRES:
				if( this.startEvent != null ) return null; // reservation not expired, not applicable
				// reservation expired, all time is reservation 
				break;
			}
		}

		if(  restrictions.getStartTime() != null 
		  && restrictions.getEndTime() != null ) {
			ZonedDateTime transactionStart = startEvent.getTimestamp().
								atZone(ZoneId.systemDefault());
			ZonedDateTime transactionEnd = lastEvent.getTimestamp().
								atZone(ZoneId.systemDefault());
			Log.debug("transaction start=%s end=%s", transactionStart, transactionEnd );
			
			ZonedDateTime start = transactionStart.with( LocalTime.parse( restrictions.getStartTime() ) );
			ZonedDateTime end = transactionStart.with( LocalTime.parse( restrictions.getEndTime() ) );
			if( start.isAfter( end ) ) end.plusDays(1);
			Log.debug("start=%s end=%s", start, end );

			TariffFlags valid = new TariffFlags();
			valid.add( 0, transactionStart.isAfter(start) && transactionStart.isBefore(end) );
			
			while( start.isBefore( transactionEnd )) {
				Log.debug("start=%s end=%s", start, end );
				
				if( start.isAfter( transactionStart )) {
					valid.add( start.toEpochSecond()-tariffStart, true );
				}
				
				if(  end.isAfter( transactionStart ) 
				  && end.isBefore( transactionEnd ) ) {
					valid.add( end.toEpochSecond()-tariffStart, false );
					
				}
				
				start = start.plusDays(1);			
				end = end.plusDays(1);			
			}
			valid.dump( "time" );
			res.and( valid );
		}

		if( restrictions.getStartDate() != null ) {
			Instant start = LocalDate.parse( restrictions.getStartDate() ).atStartOfDay().atZone( ZoneId.systemDefault() ).toInstant();

			TariffFlags valid = new TariffFlags();
			if( startEvent.getTimestamp().isBefore( start ) ) {
				// transaction.start < start
				valid.add( 0L, false );
				
				if( lastEvent.getTimestamp().isBefore(start) ) {
					// transaction.start < transaction.end < start: nothing to do
					
				} else {	
					// transaction.start < start <= transaction.end
					valid.add( start.toEpochMilli()-tariffStart, true );
					
				}
				
			} else {
				// start <= transaction.start
				valid.add( 0, true );
				
			}
			
			res.and( valid );
		}
		
		if( restrictions.getEndDate() != null ) {
			Instant end = LocalDate.parse( restrictions.getEndDate() ).atStartOfDay().atZone( ZoneId.systemDefault() ).toInstant();

			TariffFlags valid = new TariffFlags();
			if( startEvent.getTimestamp().isBefore( end ) ) {
				// transaction.start < end
				valid.add( 0L, true );
				
				if( lastEvent.getTimestamp().isBefore(end) ) {
					// transaction.start < transaction.end < end: nothing to do

				} else {
					// transaction.start < end <= transaction.end
					valid.add( end.toEpochMilli()-tariffStart, false );
					
				}
				
			} else {
				// end <= transaction.start
				valid.add( 0L, false );
				
			}
			
			res.and( valid );
		}
		
		if( restrictions.getMinKwh() != null ) {
			TariffFlags valid = tariffPeriods.threshold( false /* min */,  restrictions.getMinKwh()*1000L, (tm)->(tm.totalEnergy) );
			valid.dump( "kWh min" );
			res.and( valid );
		}

		if( restrictions.getMaxKwh() != null ) {
			TariffFlags valid = tariffPeriods.threshold( true,  restrictions.getMaxKwh()*1000L, (tm)->(tm.totalEnergy) );
			valid.dump( "kWh max" );
			res.and( valid );
		}

		if(  restrictions.getMinCurrent() != null ) {
			TariffFlags valid = tariffPeriods.threshold( false /* min */,  restrictions.getMinCurrent()*1000L, TariffMeasure::getCurrent );
			valid.dump( "current min" );
			res.and( valid );
		}

		if(  restrictions.getMaxCurrent() != null ) {
			TariffFlags valid = tariffPeriods.threshold( true /* max */,  restrictions.getMaxCurrent()*1000L, TariffMeasure::getCurrent );
			valid.dump( "current max" );
			res.and( valid );
		}

		if(  restrictions.getMinPower() != null ) {
			TariffFlags valid = tariffPeriods.threshold( false /* min */,  restrictions.getMinPower()*1000L, TariffMeasure::getPower );
			valid.dump( "current min" );
			res.and( valid );
		}

		if(  restrictions.getMaxPower() != null ) {
			TariffFlags valid = tariffPeriods.threshold( true /* max */,  restrictions.getMaxCurrent()*1000L, TariffMeasure::getPower );
			valid.dump( "current max" );
			res.and( valid );
		}

		if( restrictions.getMinDuration() != null ) {
			TariffFlags valid = tariffPeriods.threshold( false, restrictions.getMinDuration()*1000L, (tm)->(double)(tm.t) );
			valid.dump( "duration min" );
			res.and( valid );
		}

		if( restrictions.getMaxDuration() != null ) {
			TariffFlags valid = tariffPeriods.threshold( true,  restrictions.getMaxDuration()*1000L, (tm)->(double)(tm.t) );
			valid.dump( "duration max" );
			res.and( valid );
		}

		if(  restrictions.getDayOfWeek() != null ) {
			ZonedDateTime transactionStart = startEvent.getTimestamp().
					truncatedTo(ChronoUnit.SECONDS).
					atZone(ZoneId.systemDefault());
			ZonedDateTime transactionEnd = lastEvent.getTimestamp().
					truncatedTo(ChronoUnit.SECONDS).
					atZone(ZoneId.systemDefault());
			Log.debug("Tariffication.checkRestrictions.dayOfWeek: transaction start=%s end=%s", transactionStart, transactionEnd );

			ZonedDateTime start = transactionStart.with( LocalTime.parse( "00:00:00" ) );
			ZonedDateTime end = transactionStart.with( LocalTime.parse( "23:59:59" ) );
			if( start.isAfter( end ) ) end.plusDays(1);
			Log.debug("Tariffication.checkRestrictions.dayOfWeek: start=%s end=%s", start, end );

			TariffFlags valid = new TariffFlags();
			valid.add( 0L, transactionStart.isAfter(start) 
						&& transactionStart.isBefore(end)
						&& restrictions.getDayOfWeek().contains(transactionStart.getDayOfWeek() ));

			while( start.isBefore( transactionEnd )) {
				Log.debug("Tariffication.checkRestrictions.dayOfWeek: start=%s end=%s", start, end );

				if( start.isAfter( transactionStart )) {
					valid.add( start.toEpochSecond()-tariffStart,
							restrictions.getDayOfWeek().contains(start.getDayOfWeek() ));
				}

				if( end.isAfter( transactionStart ) 
				 && end.isBefore( transactionEnd ) ) {
					valid.add( end.toEpochSecond()-tariffStart, false );

				}

				start = start.plusDays(1);			
				end = end.plusDays(1);			
			}
			valid.dump( "Tariffication.checkRestrictions.dayOfWeek" );
			res.and( valid );
		}
		
		return res;
	}

	private void checkTariffs( List<OcpiTariff> tariffs ) {

		for( OcpiTariff tariff: tariffs ) {
			for( OcpiTariffElement element : tariff.getElements() ) {
		
				TariffFlags assigned = checkRestrictions( element.getRestrictions() );
				if( assigned == null ) continue; // not applicable at all
				assigned.dump( "Tariffication.checkRestrictions: assigned" );
				
				for( OcpiPriceComponent priceComponent : element.getPriceComponents() ) {
					Tuple2<OcpiTariff,OcpiPriceComponent> tariffAndElement = new Tuple2<OcpiTariff,OcpiPriceComponent>( tariff, priceComponent );
					tariffPeriods.assign( tariffAndElement, assigned );
					tariffPeriods.dump( "periods" );

				}
			}
		}
	}
	
	private void addCost( OcpiPriceComponent priceComponent, OcpiChargingPeriod chargingPeriod, TariffMeasure tm, TariffMeasure prev, boolean applyTimeStep, List<OcpiPriceComponent> usedFlats ) {
		double cost = 0.0;

		switch( priceComponent.getType() ) {
		case FLAT: {
			cost = priceComponent.getPrice();
			for( OcpiPriceComponent u : usedFlats ) {
				if( u == priceComponent ) cost = 0.0;
			}
			usedFlats.add(priceComponent);

			cdr.setTotalFixedCost( cdr.getTotalFixedCost()+cost );
			break;
		}

		case ENERGY: {
			double amount = tm.getEnergy()-prev.getEnergy();
			if( amount == 0.0 ) return;
			
			if( priceComponent.getStepSize() != null ) {
				amount = Math.ceil( amount/priceComponent.getStepSize())*priceComponent.getStepSize();
			}
			amount /= 1000.0;
			cost = priceComponent.getPrice()*amount;
			cdr.setTotalEnergy( cdr.getTotalEnergy()+amount );
			cdr.setTotalEnergyCost( cdr.getTotalEnergyCost()+cost );
			
			OcpiDimension dimension = new OcpiDimension();
			dimension.setType( CdrDimensionType.ENERGY );
			dimension.setVolume( amount );
			chargingPeriod.getDimensions().add( dimension );
			
			if( tm.getCurrent() != null ) {
				dimension = new OcpiDimension();
				dimension.setType( CdrDimensionType.CURRENT );
				dimension.setVolume( tm.getCurrent() );
				chargingPeriod.getDimensions().add( dimension );
			}
			
			if( tm.getPower() != null ) {
				dimension = new OcpiDimension();
				dimension.setType( CdrDimensionType.POWER );
				dimension.setVolume( tm.getPower() );
				chargingPeriod.getDimensions().add( dimension );
			}
			

			break;
		}

		case PARKING_TIME: {
			double amount = (tm.totalParkingTime-prev.totalParkingTime)/1000.0;
			if( amount == 0.0 ) return;
			
			if( applyTimeStep ) {
				amount = Math.ceil( amount/priceComponent.getStepSize())*priceComponent.getStepSize();
			}
			amount /= 3600.0;
			cost = priceComponent.getPrice()*amount;
			cdr.setTotalParkingTime( cdr.getTotalParkingTime()+amount );
			cdr.setTotalParkingCost( cdr.getTotalParkingCost()+cost );
			
			OcpiDimension dimension = new OcpiDimension();
			dimension.setType( CdrDimensionType.PARKING_TIME );
			dimension.setVolume( amount );
			chargingPeriod.getDimensions().add( dimension );
			break;
		}

		case TIME: {
			double amount = (tm.totalChargingTime-prev.totalChargingTime)/1000.0;
			if( amount == 0.0 ) {
				// could be is a reservation time
				amount = (tm.totalReservationTime-prev.totalReservationTime)/1000.0;
			}
			if( amount == 0.0 ) return;
			
			if( applyTimeStep ) {
				amount = priceComponent.getStepSize()*Math.ceil( amount/priceComponent.getStepSize() );
			}
			amount /= 3600.0;
			cost = priceComponent.getPrice()*amount;
			cdr.setTotalTime( cdr.getTotalTime()+amount );
			cdr.setTotalTimeCost( cdr.getTotalTimeCost()+cost );

			OcpiDimension dimension = new OcpiDimension();
			dimension.setType( CdrDimensionType.TIME );
			dimension.setVolume( amount );
			chargingPeriod.getDimensions().add( dimension );
			break;
		}}

		double vat = 0.0;
		if( priceComponent.getVat() != null ) {
			vat = cost*priceComponent.getVat()/100.0;
		}
		Log.debug( "type=%s cost=%f vat=%f", priceComponent.getType(), cost, vat );

		cdr.setTotalCost( cdr.getTotalCost()+cost+vat );
	}
	
	private void fillCost() {
		List<OcpiPriceComponent> usedFlats = new LinkedList<OcpiPriceComponent>();
		
		cdr.setTotalCost(0.0);
		cdr.setTotalFixedCost(0.0);
		cdr.setTotalEnergy(0.0);
		cdr.setTotalEnergyCost(0.0);
		cdr.setTotalTime(0.0);
		cdr.setTotalTimeCost(0.0);
		cdr.setTotalParkingTime(0.0);
		cdr.setTotalParkingCost(0.0);
		cdr.setChargingPeriods( new LinkedList<OcpiChargingPeriod>() );

		Iterator<TariffMeasure> tmIt = tariffPeriods.iterator();
		if( !tmIt.hasNext() ) return;
		
		TariffMeasure prev = tmIt.next();
		
		boolean hasNext = tmIt.hasNext();

		if( !hasNext ) {
			// only one TariffMeasure, account FLAT if any
			Tuple2<OcpiTariff, OcpiPriceComponent> t2 = prev.getComponent(OcpiPriceComponent.Type.FLAT);
			if( t2 != null ) {
				usedFlats.add( t2.f2 );
				double cost = t2.f2.getPrice();
				double vat = ( t2.f2.getVat() == null ? 0.0 : cost*t2.f2.getVat()/100.0 );
				cdr.setTotalFixedCost( cdr.getTotalFixedCost()+cost );
				cdr.setTotalCost( cdr.getTotalCost()+cost+vat );
			}
		}
		
		while( hasNext ) {
			TariffMeasure tm = tmIt.next();
			hasNext = tmIt.hasNext();

			OcpiChargingPeriod chargingPeriod = new OcpiChargingPeriod();
			chargingPeriod.setStartDateTime( Instant.ofEpochMilli( tariffStart+prev.t ) );
			chargingPeriod.setDimensions( new LinkedList<OcpiDimension>() );
			cdr.getChargingPeriods().add(chargingPeriod);
			
			for( Entry<Type, Tuple2<OcpiTariff, OcpiPriceComponent>> e : prev.componentsSet() ) {
				chargingPeriod.setTariffId( e.getValue().f1.getId() ); // Possible error in OCPI specification, should be a list

				OcpiPriceComponent priceComponent = e.getValue().f2;
				boolean applyTimeStep = false;
				if( priceComponent.getStepSize() != null ) {
					if(  !hasNext 
					  || (  tm.getComponent( Type.PARKING_TIME ) == null 
						 && tm.getComponent( Type.TIME) == null )) {
						applyTimeStep = true;
					}
				}
				
				this.addCost( e.getValue().f2, chargingPeriod, tm, prev, applyTimeStep, usedFlats );
			}
			
			prev = tm;
		}
		Log.debug( "Tariffication.evalPeriodstype: totalCost=%f", cdr.getTotalCost() );
	}

	private void fillCDR( List<OcpiTariff> tariffs ) {
		cdr = new OcpiCdr();
		
		cdr.setCountryCode( session.getCountryCode() );
		cdr.setPartyId( session.getPartyId() );
		cdr.setStartDateTime( session.getStartDatetime() );
		cdr.setEndDateTime( lastEvent.getTimestamp() );
		cdr.setSessionId( session.getId() );
		cdr.setCdrToken( session.getCdrToken() );
		cdr.setAuthMethod( session.getAuthMethod() );
		cdr.setAuthorizationReference( session.getAuthReference() );
		cdr.setCdrLocation( location);
		cdr.setMeterId( session.getMeterId() );
		cdr.setCurrency( session.getCurrency() );
		cdr.setSignedData(null); // TODO
		cdr.setLastUpdated( Instant.now() );

		tariffPeriods = new TariffMeasures();
		initChargingTimes();
		tariffPeriods.joinMeasure( collectMeasures( MeasurandEnum.ENERGY_ACTIVE_IMPORT_REGISTER, true, TariffMeasure::setEnergy ) );
		tariffPeriods.joinMeasure( collectMeasures( MeasurandEnum.CURRENT_IMPORT, false, TariffMeasure::setCurrent ) );
		tariffPeriods.joinMeasure( collectMeasures( MeasurandEnum.POWER_ACTIVE_IMPORT, false, TariffMeasure::setPower ) );
		tariffPeriods.dump( "initial periods" );
		
		checkTariffs( tariffs );
		fillCost();
		
		session.setKwh( cdr.getTotalEnergy() );
		session.setCurrency( cdr.getCurrency() );
		session.setTotalCost( cdr.getTotalCost() );
		
		sendCostUpdated( cdr.getTotalCost() );
	}

	public OcpiResult<?> setChargingPreferences(OcpiChargingPreferences chargingPreferences) {
		this.chargingPreferences = chargingPreferences;
		return OcpiResult.success( ChargingPreferencesResponse.NOT_POSSIBLE );
	}
}
