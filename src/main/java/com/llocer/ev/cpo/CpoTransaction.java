package com.llocer.ev.cpo;

import java.time.Instant;
import java.util.List;

import com.llocer.common.Tuple2;
import com.llocer.ev.csms.Transaction;
import com.llocer.ev.ocpi.modules.OcpiLocationsReceiverModule;
import com.llocer.ev.ocpi.msgs22.AuthMethod;
import com.llocer.ev.ocpi.msgs22.ChargingPreferencesResponse;
import com.llocer.ev.ocpi.msgs22.OcpiAuthorizationInfo;
import com.llocer.ev.ocpi.msgs22.OcpiCdr;
import com.llocer.ev.ocpi.msgs22.OcpiCdrToken;
import com.llocer.ev.ocpi.msgs22.OcpiChargingPreferences;
import com.llocer.ev.ocpi.msgs22.OcpiConnector;
import com.llocer.ev.ocpi.msgs22.OcpiEvse;
import com.llocer.ev.ocpi.msgs22.OcpiLocation;
import com.llocer.ev.ocpi.msgs22.OcpiSession;
import com.llocer.ev.ocpi.msgs22.OcpiSession.Status;
import com.llocer.ev.ocpi.msgs22.OcpiStartSession;
import com.llocer.ev.ocpi.msgs22.OcpiTariff;
import com.llocer.ev.ocpi.msgs22.OcpiToken;
import com.llocer.ev.ocpi.server.OcpiAgentId;
import com.llocer.ev.ocpi.server.OcpiResult;
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
		OcpiTarification.fillCDR( validTariffs, events, session ); // update session fields

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
		OcpiCdr cdr = OcpiTarification.fillCDR( validTariffs, events, session );
//		Log.dump( "CDR=%s", cdr.toString().replaceAll(",", "\n" ) );
		cdr.setCdrLocation( location);
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

	public OcpiResult<?> setChargingPreferences(OcpiChargingPreferences chargingPreferences) {
		this.chargingPreferences = chargingPreferences;
		return OcpiResult.success( ChargingPreferencesResponse.NOT_POSSIBLE );
	}
}
