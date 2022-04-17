package com.llocer.ev.csms;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.llocer.common.Log;
import com.llocer.common.Tuple2;
import com.llocer.ev.ocpp.msgs20.AuthorizationStatusEnum;
import com.llocer.ev.ocpp.msgs20.MeasurandEnum;
import com.llocer.ev.ocpp.msgs20.OcppChargingProfile;
import com.llocer.ev.ocpp.msgs20.OcppClearChargingProfile;
import com.llocer.ev.ocpp.msgs20.OcppClearChargingProfileRequest;
import com.llocer.ev.ocpp.msgs20.OcppCostUpdatedRequest;
import com.llocer.ev.ocpp.msgs20.OcppEVSE;
import com.llocer.ev.ocpp.msgs20.OcppGetCompositeScheduleRequest;
import com.llocer.ev.ocpp.msgs20.OcppIdTokenInfo;
import com.llocer.ev.ocpp.msgs20.OcppSampledValue;
import com.llocer.ev.ocpp.msgs20.OcppSetChargingProfileRequest;
import com.llocer.ev.ocpp.msgs20.OcppTransactionEventRequest;
import com.llocer.ev.ocpp.msgs20.OcppTransactionEventResponse;
import com.llocer.ev.ocpp.msgs20.TransactionEventEnum;
import com.llocer.ev.ocpp.msgs20.TriggerReasonEnum;
import com.llocer.ev.ocpp.server.OcppCommand;

public class Transaction {
	private static Comparator<OcppTransactionEventRequest> compareSeqNo = new Comparator<OcppTransactionEventRequest>() {
		@Override
		public int compare(OcppTransactionEventRequest o1, OcppTransactionEventRequest o2) {
            return o1.getSeqNo().compareTo( o2.getSeqNo() );
        }
	};
	
	protected void store() {}; // usual override
	protected void remove() {}; // usual override

	/*
	 * fields
	 */
	
	public String csId;
	public String transactionId;
	private OcppEVSE evse;
	@JsonIgnore
	public ChargingStation cs;
	
	List<OcppTransactionEventRequest> events = new LinkedList<OcppTransactionEventRequest>();
	private TriggerReasonEnum evState = TriggerReasonEnum.EV_DEPARTED;
	public OcppTransactionEventRequest startEvent = null;
//	public OcppTransactionEventRequest endEvent = null;
	public OcppTransactionEventRequest lastEvent = null;
	private OcppIdTokenInfo idTokenInfo = null;

	private OcppChargingProfile chargingProfile = null;

//	protected Transaction( ChargingStation chargingStation ) {
//		this.cs = chargingStation;
//	}
	
//	protected void setCS( ChargingStation cs ) {
//		this.csId = cs.getId();
//		this.cs = cs;
//	}
	
	public void setIdTokenInfo( OcppIdTokenInfo idTokenInfo) {
		this.idTokenInfo = idTokenInfo;
	}

	public OcppTransactionEventResponse onEvent( OcppTransactionEventRequest event ) throws Exception {
		OcppTransactionEventResponse response = new OcppTransactionEventResponse();

		/*
		 * store event 
		 */
		
		events.add( event );
		Log.debug( "Transaction.onEvent: events.size=%d", events.size() );
		
		switch( event.getEventType() ) {
		case STARTED:
			this.startEvent = event;
			break;
		case UPDATED:
			break;
		case ENDED:
//			this.endEvent = event;
			break;
		}
		
		if( this.lastEvent != null && this.lastEvent.getSeqNo() >= event.getSeqNo() ) {
			// event out of order
			Collections.sort( events, compareSeqNo );
			return response;
		}
		
		this.lastEvent = event;
		
		/*
		 * handle event 
		 */
		
		TriggerReasonEnum triggerReason = event.getTriggerReason();
		switch( triggerReason ) {
		case EV_DETECTED:
		case CABLE_PLUGGED_IN:
		case EV_COMMUNICATION_LOST:
		case EV_CONNECT_TIMEOUT:
		case EV_DEPARTED:
			evState = triggerReason;
			break;
			
		case AUTHORIZED:
			this.idTokenInfo = this.cs.getCSMS().authorize( this.cs, event.getIdToken() ); 
			response.setIdTokenInfo( idTokenInfo );
			break;
			
		case DEAUTHORIZED:
			this.idTokenInfo = null;
			return response;
			
//		case CHARGING_STATE_CHANGED:
//		case CHARGING_RATE_CHANGED:
//		case ENERGY_LIMIT_REACHED:
//		case METER_VALUE_CLOCK:
//		case METER_VALUE_PERIODIC:
//		case SIGNED_DATA_RECEIVED:
//		case REMOTE_START:
//		case REMOTE_STOP:
//		case RESET_COMMAND:
//		case STOP_AUTHORIZED:
//		case TIME_LIMIT_REACHED:
//		case TRIGGER:
//		case UNLOCK_COMMAND:
//		case ABNORMAL_CONDITION:
			
		default:
			break;
		
		}

		if( event.getEvse() != null ) {
			this.evse = event.getEvse();
		}
		
		return response;
	}
	
	/*
	 * measurements and tariff
	 */
	
	static double getValue( OcppSampledValue sampleValue ) {
		double res = sampleValue.getValue(); 
		
		if( sampleValue.getUnitOfMeasure() != null ) {
			if( sampleValue.getUnitOfMeasure().getMultiplier() != null ) {
				res *= Math.pow( 10, sampleValue.getUnitOfMeasure().getMultiplier() );
			}

			if( sampleValue.getUnitOfMeasure().getUnit() == null ) {
				sampleValue.getUnitOfMeasure().setUnit( "Wh" ); // default
			}
			
			switch( sampleValue.getUnitOfMeasure().getUnit() ) {
			case "kWh":
				res *= 1000;
				break;
			default:
				break;
			}
		}

		return res;
	}
	
	
	public Iterator<OcppTransactionEventRequest> eventsIterator() {
		return events.iterator();
	}
	
	public Iterator<Tuple2<Instant,Double>> samplesIterator( MeasurandEnum measurand ) {
		return new SampledValueIterator( this, measurand );
	}

	
	boolean isFinished() {
		if( startEvent == null ) return false;
		if( lastEvent.getEventType() != TransactionEventEnum.ENDED ) return false;
		
		int seqNo = startEvent.getSeqNo();
		for( OcppTransactionEventRequest ev : events ) {
			if( ev.getSeqNo() != seqNo ) return false;
			seqNo++;
		}
		
		return true;
	}
	
	@JsonIgnore
	public boolean isAuthorized() {
		return(  this.idTokenInfo != null 
			  && this.idTokenInfo.getStatus() == AuthorizationStatusEnum.ACCEPTED );
	}
	
	@JsonIgnore
	public boolean isEvPresent() {
		switch( this.evState ) {
		case EV_DETECTED: 
		case CABLE_PLUGGED_IN: 
			return true;
		default: 
			return false;
		}
	}
	
//	public boolean isCharging() {
//		switch( this.chargingState ) {
//		case CHARGING:
//		case SUSPENDED_EV:
//			return true;
//		default:
//			return false;
//		}
//	}
	
	public OcppChargingProfile getChargingProfile() {
		return this.chargingProfile;
		
	}

	public void setChargingProfile( 
			OcppChargingProfile chargingProfile, 
			Consumer<OcppCommand> callback ) {
		
		this.chargingProfile  = chargingProfile;
		
		OcppSetChargingProfileRequest msg = new OcppSetChargingProfileRequest();
		
		if( this.evse != null ) {
			msg.setEvseId( this.evse.getId() ); 
		}
		
		chargingProfile.setTransactionId( this.transactionId );
		msg.setChargingProfile( chargingProfile );
		
		this.cs.send( msg, callback );
	}
	

	public boolean deleteChargingProfile( Consumer<OcppCommand> callback ) {
		if( this.chargingProfile == null ) return false;
		
		OcppClearChargingProfile chargingProfileCriteria = new OcppClearChargingProfile();
		if( this.evse != null ) {
			chargingProfileCriteria.setEvseId( this.evse.getId() ); 
		}
		chargingProfileCriteria.setChargingProfilePurpose( this.chargingProfile.getChargingProfilePurpose());
		chargingProfileCriteria.setStackLevel( this.chargingProfile.getStackLevel());
		
		OcppClearChargingProfileRequest msg = new OcppClearChargingProfileRequest();
		msg.setChargingProfileCriteria( chargingProfileCriteria );
		
		this.cs.send( msg, callback );
		
		return true;
	}
	

	public void getCompositeSchedule( Integer duration,  Consumer<OcppCommand> callback ) {
		OcppGetCompositeScheduleRequest ocppGetCompositeScheduleRequest = new OcppGetCompositeScheduleRequest();
		ocppGetCompositeScheduleRequest.setDuration(duration);
		if( this.evse != null ) {
			ocppGetCompositeScheduleRequest.setEvseId( this.evse.getId() ); 
		}
		
		this.cs.send( ocppGetCompositeScheduleRequest, callback );
	}

	protected void sendCostUpdated( double totalCost ) {
		OcppCostUpdatedRequest costUpdated = new OcppCostUpdatedRequest();
		costUpdated.setTransactionId( this.transactionId );
		costUpdated.setTotalCost(totalCost );
	}
}
