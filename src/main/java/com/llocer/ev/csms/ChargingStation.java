package com.llocer.ev.csms;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectReader;
import com.llocer.collections.SimpleMapFactory;
import com.llocer.common.Log;
import com.llocer.common.SimpleMap;
import com.llocer.ev.ocpp.msgs20.GenericStatusEnum;
import com.llocer.ev.ocpp.msgs20.NotifyEVChargingNeedsStatusEnum;
import com.llocer.ev.ocpp.msgs20.OcppAuthorizationData;
import com.llocer.ev.ocpp.msgs20.OcppAuthorizeRequest;
import com.llocer.ev.ocpp.msgs20.OcppAuthorizeResponse;
import com.llocer.ev.ocpp.msgs20.OcppBootNotificationRequest;
import com.llocer.ev.ocpp.msgs20.OcppBootNotificationResponse;
import com.llocer.ev.ocpp.msgs20.OcppClearedChargingLimitResponse;
import com.llocer.ev.ocpp.msgs20.OcppFirmwareStatusNotificationResponse;
import com.llocer.ev.ocpp.msgs20.OcppHeartbeatResponse;
import com.llocer.ev.ocpp.msgs20.OcppIdTokenInfo;
import com.llocer.ev.ocpp.msgs20.OcppLogStatusNotificationResponse;
import com.llocer.ev.ocpp.msgs20.OcppMeterValuesResponse;
import com.llocer.ev.ocpp.msgs20.OcppNotifyChargingLimitResponse;
import com.llocer.ev.ocpp.msgs20.OcppNotifyCustomerInformationResponse;
import com.llocer.ev.ocpp.msgs20.OcppNotifyDisplayMessagesResponse;
import com.llocer.ev.ocpp.msgs20.OcppNotifyEVChargingNeedsResponse;
import com.llocer.ev.ocpp.msgs20.OcppNotifyEVChargingScheduleResponse;
import com.llocer.ev.ocpp.msgs20.OcppNotifyEventResponse;
import com.llocer.ev.ocpp.msgs20.OcppNotifyMonitoringReportResponse;
import com.llocer.ev.ocpp.msgs20.OcppNotifyReportResponse;
import com.llocer.ev.ocpp.msgs20.OcppPublishFirmwareStatusNotificationResponse;
import com.llocer.ev.ocpp.msgs20.OcppReportChargingProfilesResponse;
import com.llocer.ev.ocpp.msgs20.OcppReservationStatusUpdateRequest;
import com.llocer.ev.ocpp.msgs20.OcppReservationStatusUpdateResponse;
import com.llocer.ev.ocpp.msgs20.OcppSecurityEventNotificationResponse;
import com.llocer.ev.ocpp.msgs20.OcppSendLocalListRequest;
import com.llocer.ev.ocpp.msgs20.OcppSendLocalListResponse;
import com.llocer.ev.ocpp.msgs20.OcppSignCertificateRequest;
import com.llocer.ev.ocpp.msgs20.OcppSignCertificateResponse;
import com.llocer.ev.ocpp.msgs20.OcppStatusNotificationRequest;
import com.llocer.ev.ocpp.msgs20.OcppStatusNotificationResponse;
import com.llocer.ev.ocpp.msgs20.OcppTransactionEventRequest;
import com.llocer.ev.ocpp.msgs20.OcppTransactionEventResponse;
import com.llocer.ev.ocpp.msgs20.OcppUpdateFirmwareRequest;
import com.llocer.ev.ocpp.msgs20.RegistrationStatusEnum;
import com.llocer.ev.ocpp.msgs20.SendLocalListStatusEnum;
import com.llocer.ev.ocpp.msgs20.UpdateEnum;
import com.llocer.ev.ocpp.server.OcppEndpoint;
import com.llocer.ev.ocpp.server.OcppAction;
import com.llocer.ev.ocpp.server.OcppAgent;
import com.llocer.ev.ocpp.server.OcppCommand;
import com.llocer.ev.server.OAMAgent;
import com.llocer.ev.server.ServletOAM;

class SendOcppUpdateFirmwareCommand extends Command {
	public String cs;
	public OcppUpdateFirmwareRequest message;
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SendOcppUpdateFirmwareCommand.class, name = "sendUpdateFirmware")
})
class Command {
	public String type;
}

public class ChargingStation implements OcppAgent, OAMAgent {
	
	/*
	 * static
	 */
	
	private enum BootState {
		NO_BOOT,
		CONFIGURING,
		CONFIGURED,
		READY
	}
	
	private static class State {
		public BootState bootState = BootState.NO_BOOT;
		public int localListVersionNumber = 1; // tokens local list
	}
	
	private static final SimpleMapFactory mapFactory = SimpleMapFactory.forName( "com.llocer.redis.RedisMapFactory" );

	private static final ObjectReader readerOAM = ServletOAM.mapper.readerFor( Command.class );

	private static final Map<String /*csId*/,ChargingStation> chargingStations = new HashMap<String,ChargingStation>();
	private static final Map<String /*csmsId*/,CSMS> csmss = new HashMap<String,CSMS>();
	private static final SimpleMap<String,State> chargingStationStates = mapFactory.make( "CS::", State.class );
	
	/*
	 * object fields
	 */
	
	public final String id;
	private final ChargingStationConfig config;
	private final State state;
	
	private CSMS csms;
	private OcppEndpoint ocppEndpoint = null;
	
	private Iterator<BootCommand> runningConfigCmnds = null;
	@SuppressWarnings("unused")
	private OcppBootNotificationRequest bootNotification = null;
	
	/*
	 * object methods
	 */
	
	private ChargingStation( String id ) {
		this.id = id;
		
		this.config = ChargingStationConfig.read( id );
		
		State state = chargingStationStates.get( id );
		if( state == null ) {
			state = new State();
			chargingStationStates.put( id,  state );
		}
		this.state = state;
	}
	
	public static ChargingStation make( String id ) {
		ChargingStation it = new ChargingStation( id );
		chargingStations.put( it.id, it );
		OcppEndpoint.putAgent( it );
		ServletOAM.registerAgent(id, it);
		return it;
	}

	public static ChargingStation getChargingStation(String csId) {
		return chargingStations.get( csId );
	}

	public static void registerCSMS(String csmsId, CSMS csms) {
		csmss.put( csmsId, csms );	
	}

	public static CSMS getCSMS( String csmsId ) {
		return csmss.get( csmsId );	
	}

	public String getId() {
		return id;
	}
	
	public CSMS getCSMS() {
		if( this.csms == null ) {
			this.csms = csmss.get( this.config.csms );
			if( this.csms == null ) {
				Log.error( "ChargingStation.getCSMS: unknown csms=%s", this.config.csms );
				throw new IllegalStateException();
			}
		}
		return this.csms;
	}
	
	void updateState() {
		chargingStationStates.put( id, state );
	}

	
	/******************************************************************************
	 * OCPP messages
	 */
	
	public void send( Object payload, Consumer<OcppCommand> callback ) {
		this.ocppEndpoint.sendAction( payload, callback );
	}
	
	public void onOcppEndpointConnected( OcppEndpoint ws ) {
		// ws == null => disconnected
		this.ocppEndpoint = ws;
	}
	
	private OcppTransactionEventResponse onTransactionEvent( OcppTransactionEventRequest event ) throws Exception {

		String transactionId = event.getTransactionInfo().getTransactionId();
		
		Transaction transaction = getCSMS().getTransactionById( transactionId, this, event.getReservationId() );
		
		OcppTransactionEventResponse response = transaction.onEvent( event );
		
		if( transaction.isFinished() ) {
			// transaction finished, remove it
			transaction.remove();
		}
		
		return response; 
	}
	
	private Object handleOcppCall(OcppAction action, Object payload) throws Exception {
		switch( action ) {
		// security:
		case SecurityEventNotification:
			Log.warning(  "%s: %s %s", id, action, payload );
			return new OcppSecurityEventNotificationResponse();
			
		case SignCertificate: {
			OcppSignCertificateRequest signCertificateRequest = (OcppSignCertificateRequest)payload;
			
			boolean accepted = false;
			switch( signCertificateRequest.getCertificateType() ) {
			case CHARGING_STATION_CERTIFICATE:
				 accepted = getCSMS().signChargingStationCSR( signCertificateRequest.getCsr() );
				break;
			case V_2_G_CERTIFICATE:
				break;
			}
			OcppSignCertificateResponse signCertificateResponse = new OcppSignCertificateResponse();
			signCertificateResponse.setStatus( accepted ? GenericStatusEnum.ACCEPTED : GenericStatusEnum.REJECTED );
			return signCertificateResponse;
		}

		// Provisioning:
		case BootNotification: {
			// TODO: send updated list of local tokens
			
			this.bootNotification  = (OcppBootNotificationRequest)payload;
			
			OcppBootNotificationResponse response = new OcppBootNotificationResponse();
			response.setCurrentTime( Instant.now() );
			response.setStatusInfo( null );
			
			if( state.bootState == BootState.READY ) state.bootState = BootState.NO_BOOT;
			
			if( state.bootState == BootState.NO_BOOT && config.boot_commands != null ) {
				runningConfigCmnds = config.boot_commands.iterator();
				if( runningConfigCmnds.hasNext() ) {
					state.bootState = BootState.CONFIGURING;
					ocppEndpoint.sendAction( runningConfigCmnds.next()  );

					response.setStatus( RegistrationStatusEnum.PENDING );
					response.setInterval( config.bootInterval ); 
					return response;

				}
			}
			
			state.bootState = BootState.READY;
			
			response.setStatus( RegistrationStatusEnum.ACCEPTED );
			response.setInterval( OcppEndpoint.config.heartbeatInterval );
			
			return response;
		}

		case NotifyReport:
			Log.info(  "%s: %s %s", id, action, payload );
			return new OcppNotifyReportResponse();

		// Authorization:
		case Authorize: {
			OcppAuthorizeRequest request = (OcppAuthorizeRequest)payload;
			OcppAuthorizeResponse response = new OcppAuthorizeResponse();
			OcppIdTokenInfo idTokenInfo = getCSMS().authorize( this, request.getIdToken() ); 
			response.setIdTokenInfo( idTokenInfo );
			return response;
		}
			
		// Transactions:
		case TransactionEvent: {
			OcppTransactionEventRequest request  = (OcppTransactionEventRequest)payload;
			return onTransactionEvent( request );
		}

		// Availability  
		case StatusNotification: {
			OcppStatusNotificationRequest request  = (OcppStatusNotificationRequest)payload;
			getCSMS().handleStatusNotification( this, request );
			return new OcppStatusNotificationResponse();
		}
		
		case Heartbeat: {
			OcppHeartbeatResponse response = new OcppHeartbeatResponse();
			response.setCurrentTime( Instant.now() );
			return response;
		}
		

		// Reservation
		case ReservationStatusUpdate: {
			OcppReservationStatusUpdateRequest request = (OcppReservationStatusUpdateRequest)payload;
			switch( request.getReservationUpdateStatus() ) {
			case EXPIRED:
				this.csms.reservationFinished( this, request.getReservationId(), false );
				break;
				
			case REMOVED:
				this.csms.reservationFinished( this, request.getReservationId(), true );
				break;
			}
			return new OcppReservationStatusUpdateResponse();
		}

		// Tariff and cost

		// Meter values
		case MeterValues:
			Log.info(  "%s: %s %s", id, action, payload );
			return new OcppMeterValuesResponse();
		
		// Smart charging
		case ReportChargingProfiles:
			Log.info(  "%s: %s %s", id, action, payload );
			return new OcppReportChargingProfilesResponse();
			
		case NotifyChargingLimit:
			Log.info(  "%s: %s %s", id, action, payload );
			return new OcppNotifyChargingLimitResponse();
			
		case ClearedChargingLimit:
			Log.info(  "%s: %s %s", id, action, payload );
			return new OcppClearedChargingLimitResponse();
			
		case NotifyEVChargingNeeds: {
			OcppNotifyEVChargingNeedsResponse response = new OcppNotifyEVChargingNeedsResponse();
			response.setStatus( NotifyEVChargingNeedsStatusEnum.ACCEPTED );
			return response;
		}
			
		case NotifyEVChargingSchedule: {
			OcppNotifyEVChargingScheduleResponse response = new OcppNotifyEVChargingScheduleResponse();
			response.setStatus( GenericStatusEnum.ACCEPTED );
			return response;
		}

		// Firmware management
		case FirmwareStatusNotification:
			return new OcppFirmwareStatusNotificationResponse();
			
		case PublishFirmwareStatusNotification:
			return new OcppPublishFirmwareStatusNotificationResponse();
			
		// ISO 15118 CertificateManagement
		case Get15118EVCertificate:
			// TODO: Get15118EVCertificate
			return ChargingStationError.NotSupported;
			
		case GetCertificateStatus:
			// TODO: GetCertificateStatus
			return ChargingStationError.NotSupported;
			
		// Diagnostics
		case LogStatusNotification: 
			return new OcppLogStatusNotificationResponse();
		
		case NotifyMonitoringReport: 
			return new OcppNotifyMonitoringReportResponse();
			
		case NotifyEvent:
			return new OcppNotifyEventResponse();
			
		case NotifyCustomerInformation:
			return new OcppNotifyCustomerInformationResponse();

		// Display message
		case NotifyDisplayMessages:
			return new OcppNotifyDisplayMessagesResponse();

		// Data transfer

		// Unknown
		default:
			return ChargingStationError.NotImplemented;
		} // end switch
		
	}
	
	@Override
	public Object onOcppCall(OcppAction action, Object payload) throws Exception {
		switch( state.bootState ) {
		case NO_BOOT:
			if( action != OcppAction.BootNotification ) return ChargingStationError.WaitingBoot; 
			break;
			
		case CONFIGURING:
			return ChargingStationError.WaitingBoot; 
			
		case CONFIGURED:
			if( action != OcppAction.BootNotification ) return ChargingStationError.WaitingBoot; 
			break;
			
		case READY:
			break;
		}
		
		Object res = handleOcppCall( action, payload );
		updateState();
		return res;
	}

	@Override
	public void onOcppCallResult( OcppCommand command ) {
		switch( state.bootState ) {
		case NO_BOOT:
			// ignore
			break;
			
		case CONFIGURING:
			if( runningConfigCmnds.hasNext() ) {
				ocppEndpoint.sendAction( runningConfigCmnds.next()  );
				
			} else {
				state.bootState = BootState.CONFIGURED;
				
			}
			break;
			
		case CONFIGURED:
			break;
			
		case READY:
			break;
		}
		
		if( command.callback != null ) {
			command.callback.accept( command );
		}
		
		updateState();
	}

	@Override
	public void onOcppCallError( OcppCommand command ) {
		switch( state.bootState ) {
		case NO_BOOT:
		case CONFIGURING:
		case CONFIGURED:
			state.bootState = BootState.NO_BOOT;
			updateState();

			break;
			
		case READY:
			break;
		}
		
		if( command.callback == null ) return;
		command.callback.accept( command );
		
		updateState();

	}
	
	/******************************************************************************
	 * Tokens
	 */

	public void sendLocalToken( OcppAuthorizationData ocppAuthorizationData ) {
		if( this.ocppEndpoint == null ) return;
		
		OcppSendLocalListRequest ocppSendLocalListRequest = new OcppSendLocalListRequest();
		ocppSendLocalListRequest.setVersionNumber( state.localListVersionNumber );
		ocppSendLocalListRequest.setUpdateType( UpdateEnum.DIFFERENTIAL );
		ocppSendLocalListRequest.setLocalAuthorizationList( Collections.singletonList(ocppAuthorizationData));
		
		state.localListVersionNumber++;
		
		send( ocppSendLocalListRequest, (ocppCommand)->{ 
			SendLocalListStatusEnum status = SendLocalListStatusEnum.FAILED;
			
			if( ocppCommand.error == null ) {
				OcppSendLocalListResponse ocppSendLocalListResponse = (OcppSendLocalListResponse) ocppCommand.answer;
				status = ocppSendLocalListResponse.getStatus();
			}
			
			if( status != SendLocalListStatusEnum.FAILED ) {
				Log.warning( "CSEndpoint.sendLocalToken: status=%s", status );
			}
		
		});
		
		updateState();
	}

	public void removeLocalToken(OcppAuthorizationData ocppAuthorizationData) {
		if( this.ocppEndpoint == null ) return;
		
		ocppAuthorizationData.setIdTokenInfo(null); // means remove
		
		OcppSendLocalListRequest ocppSendLocalListRequest = new OcppSendLocalListRequest();
		ocppSendLocalListRequest.setVersionNumber( state.localListVersionNumber );
		ocppSendLocalListRequest.setUpdateType( UpdateEnum.DIFFERENTIAL );
		ocppSendLocalListRequest.setLocalAuthorizationList( Collections.singletonList(ocppAuthorizationData));
		
		state.localListVersionNumber++;
		
		send( ocppSendLocalListRequest, (ocppCommand)->{ 
			SendLocalListStatusEnum status = SendLocalListStatusEnum.FAILED;
			
			if( ocppCommand.error == null ) {
					OcppSendLocalListResponse ocppSendLocalListResponse = (OcppSendLocalListResponse) ocppCommand.answer;
					status = ocppSendLocalListResponse.getStatus();
			}
		
			if( status != SendLocalListStatusEnum.FAILED ) {
				Log.warning( "CSEndpoint.sendLocalToken: status=%s", status );
			}
		});
		
		updateState();
	}
	
	/************************************************************************
	 * O&M
	 */
	
	private Object executeOAM( Command command ) {
		switch( command.type ) {
		case "SendOcppUpdateFirmwareCommand": {
			SendOcppUpdateFirmwareCommand tcommand = (SendOcppUpdateFirmwareCommand)command;
			this.send( tcommand.message, null );
			return 200;
		}
		
		default: return 404;
		}
	}
	
	@Override
	public void executeOAM(String[] uri, HttpServletRequest request, HttpServletResponse response) throws Exception {
		Command command = readerOAM.readValue( request.getInputStream() );
		Object answer = executeOAM( command);
		ServletOAM.fillResponse( response, answer );
	}

}
