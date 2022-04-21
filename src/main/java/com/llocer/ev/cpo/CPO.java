package com.llocer.ev.cpo;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.llocer.collections.SimpleMapFactory;
import com.llocer.common.Log;
import com.llocer.common.SimpleMap;
import com.llocer.common.Tuple2;
import com.llocer.ev.csms.CSMS;
import com.llocer.ev.csms.ChargingStation;
import com.llocer.ev.ocpi.modules.OcpiCDRsSenderModule;
import com.llocer.ev.ocpi.modules.OcpiChargingProfilesReceiverModule;
import com.llocer.ev.ocpi.modules.OcpiChargingProfilesReceiverModule.OcpiChargingProfilesReceiver;
import com.llocer.ev.ocpi.modules.OcpiCommandsReceiverModule;
import com.llocer.ev.ocpi.modules.OcpiCommandsReceiverModule.OcpiCommandsReceiver;
import com.llocer.ev.ocpi.modules.OcpiLocationsReceiverModule;
import com.llocer.ev.ocpi.modules.OcpiLocationsReceiverModule.OcpiLocationsReceiver;
import com.llocer.ev.ocpi.modules.OcpiLocationsSenderModule;
import com.llocer.ev.ocpi.modules.OcpiLocationsSenderModule.OcpiLocationsSender;
import com.llocer.ev.ocpi.modules.OcpiSender;
import com.llocer.ev.ocpi.modules.OcpiSessionsSenderModule;
import com.llocer.ev.ocpi.modules.OcpiSessionsSenderModule.OcpiSessionsSender;
import com.llocer.ev.ocpi.modules.OcpiTariffsReceiverModule;
import com.llocer.ev.ocpi.modules.OcpiTariffsReceiverModule.OcpiTariffsReceiver;
import com.llocer.ev.ocpi.modules.OcpiTariffsSenderModule;
import com.llocer.ev.ocpi.modules.OcpiTokensReceiverModule;
import com.llocer.ev.ocpi.modules.OcpiTokensReceiverModule.OcpiTokensReceiver;
import com.llocer.ev.ocpi.msgs.HasLastUpdated;
import com.llocer.ev.ocpi.msgs22.AuthMethod;
import com.llocer.ev.ocpi.msgs22.OcpiActiveChargingProfile;
import com.llocer.ev.ocpi.msgs22.OcpiActiveChargingProfileResult;
import com.llocer.ev.ocpi.msgs22.OcpiAuthorizationInfo;
import com.llocer.ev.ocpi.msgs22.OcpiAuthorizationInfo.Allowed;
import com.llocer.ev.ocpi.msgs22.OcpiCancelReservation;
import com.llocer.ev.ocpi.msgs22.OcpiCdr;
import com.llocer.ev.ocpi.msgs22.OcpiCdrToken;
import com.llocer.ev.ocpi.msgs22.OcpiChargingPreferences;
import com.llocer.ev.ocpi.msgs22.OcpiChargingProfile;
import com.llocer.ev.ocpi.msgs22.OcpiChargingProfileResponse;
import com.llocer.ev.ocpi.msgs22.OcpiChargingProfileResponse.ChargingProfileResponseType;
import com.llocer.ev.ocpi.msgs22.OcpiChargingProfileResult;
import com.llocer.ev.ocpi.msgs22.OcpiClientInfo;
import com.llocer.ev.ocpi.msgs22.OcpiCommandResponse;
import com.llocer.ev.ocpi.msgs22.OcpiCommandResult;
import com.llocer.ev.ocpi.msgs22.OcpiConnector;
import com.llocer.ev.ocpi.msgs22.OcpiCredentials;
import com.llocer.ev.ocpi.msgs22.OcpiCredentialsRole;
import com.llocer.ev.ocpi.msgs22.OcpiEndpoint.Identifier;
import com.llocer.ev.ocpi.msgs22.OcpiEndpoint.InterfaceRole;
import com.llocer.ev.ocpi.msgs22.OcpiEvse;
import com.llocer.ev.ocpi.msgs22.OcpiEvse.EvseStatus;
import com.llocer.ev.ocpi.msgs22.OcpiLocation;
import com.llocer.ev.ocpi.msgs22.OcpiReserveNow;
import com.llocer.ev.ocpi.msgs22.OcpiSession;
import com.llocer.ev.ocpi.msgs22.OcpiStartSession;
import com.llocer.ev.ocpi.msgs22.OcpiStopSession;
import com.llocer.ev.ocpi.msgs22.OcpiTariff;
import com.llocer.ev.ocpi.msgs22.OcpiToken;
import com.llocer.ev.ocpi.msgs22.OcpiToken.TokenType;
import com.llocer.ev.ocpi.msgs22.OcpiToken.Whitelist;
import com.llocer.ev.ocpi.msgs22.OcpiUnlockConnector;
import com.llocer.ev.ocpi.server.OcpiAgentId;
import com.llocer.ev.ocpi.server.OcpiConfig;
import com.llocer.ev.ocpi.server.OcpiLink;
import com.llocer.ev.ocpi.server.OcpiRequestData;
import com.llocer.ev.ocpi.server.OcpiRequestData.HttpMethod;
import com.llocer.ev.ocpi.server.OcpiResult;
import com.llocer.ev.ocpi.server.OcpiResult.OcpiResultEnum;
import com.llocer.ev.ocpp.msgs20.ChargingProfileStatusEnum;
import com.llocer.ev.ocpp.msgs20.GenericStatusEnum;
import com.llocer.ev.ocpp.msgs20.IdTokenEnum;
import com.llocer.ev.ocpp.msgs20.OcppAuthorizationData;
import com.llocer.ev.ocpp.msgs20.OcppCancelReservationRequest;
import com.llocer.ev.ocpp.msgs20.OcppCancelReservationResponse;
import com.llocer.ev.ocpp.msgs20.OcppChargingProfile;
import com.llocer.ev.ocpp.msgs20.OcppCompositeSchedule;
import com.llocer.ev.ocpp.msgs20.OcppGetCompositeScheduleResponse;
import com.llocer.ev.ocpp.msgs20.OcppIdToken;
import com.llocer.ev.ocpp.msgs20.OcppIdTokenInfo;
import com.llocer.ev.ocpp.msgs20.OcppRequestStartTransactionRequest;
import com.llocer.ev.ocpp.msgs20.OcppRequestStartTransactionResponse;
import com.llocer.ev.ocpp.msgs20.OcppRequestStopTransactionRequest;
import com.llocer.ev.ocpp.msgs20.OcppReserveNowRequest;
import com.llocer.ev.ocpp.msgs20.OcppReserveNowResponse;
import com.llocer.ev.ocpp.msgs20.OcppSetChargingProfileResponse;
import com.llocer.ev.ocpp.msgs20.OcppStatusNotificationRequest;
import com.llocer.ev.ocpp.msgs20.OcppUnlockConnectorRequest;
import com.llocer.ev.ocpp.msgs20.OcppUnlockConnectorResponse;
import com.llocer.ev.ocpp.server.OcppEndpoint;
import com.llocer.ev.ocpp.server.OcppException;
import com.llocer.ev.server.OAMAgent;
import com.llocer.ev.server.ServletOAM;

class OcpiMakeLinkCommand extends CpoOAMCommand {
	public OcpiAgentId cpo;
	public String ownToken;
	public OcpiClientInfo.Role peerRole;
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OcpiMakeLinkCommand.class, name = "ocpiMakeLink")
})
class CpoOAMCommand {
	public String type;
}

class CpoChargingStationConfig {
	public String id;
	public String location;
}

class CpoTokenConfig {
	public IdTokenEnum idTokenEnum;
	public Whitelist whitelist;
	public TokenType tokenType;
}

class CpoConfig {
	public OcpiAgentId id;
	public List< CpoChargingStationConfig > charging_stations;
	public Whitelist allowed_whitelist = Whitelist.ALWAYS;
	public List<CpoTokenConfig> token_config;
	public OcpiAgentId default_EMSP;
}

public class CPO implements OcpiAgent, OAMAgent, CSMS,
		OcpiSender, OcpiSessionsSender, OcpiLocationsSender, 
		OcpiCommandsReceiver, OcpiChargingProfilesReceiver, 
		OcpiLocationsReceiver, OcpiTariffsReceiver, OcpiTokensReceiver {
	
	static final SimpleMapFactory mapFactory = SimpleMapFactory.forName( "com.llocer.redis.RedisMapFactory" );
	
	private	static final ObjectReader readerConfig = ServletOAM.mapper.readerFor( CpoConfig.class );
	private static final ObjectReader readerOAM = ServletOAM.mapper.readerFor( CpoOAMCommand.class );
	
	private final OcpiAgentId id;
	final CpoConfig config;

	// locations && ChargingStations
	
	private final Map< String /*csId*/, String /*locationId*/ > locationByCsId = new HashMap<String,String>();
	private final SimpleMap<String /*locationId*/,OcpiLocation> locations;
	private final OcpiLocationsSenderModule locationsSenderModule;
	private final OcpiLocationsReceiverModule locationsReceiverModule;
	private final Map< Tuple2< String /*locationId*/, String /*evseId*/ >,
	                   Tuple2< ChargingStation, Integer /* evseIdx */ > > ocppCSEvses = 
	    new HashMap< Tuple2< String /*locationId*/, String /*evseId*/ >,
	                 Tuple2< ChargingStation, Integer /* evseIdx */ > >();

	// sessions, transactions 

	private final OcpiSessionsSenderModule sessionsModule;
	private final SimpleMap< String /*sessionId*/, CpoTransaction > ocppTransactions; 
	private Map< Tuple2< String /*csId*/, String /*transactionId*/ >, CpoTransaction > transactionsByCsAndId = null; 

	// tariffs, CDRs, charging profiles

	private final OcpiTariffsSenderModule tariffsSenderModule;
	private final OcpiTariffsReceiverModule tariffsReceiverModule;
	private final OcpiCDRsSenderModule cdrsModule;
	
	private final SimpleMap< String /*tariffId*/, OcpiTariff > tariffsById;

	private final Map< OcpiAgentId /*eMSP*/, SimpleMap<String, OcpiCdr> > CDRs = 
			new HashMap< OcpiAgentId, SimpleMap<String, OcpiCdr> >();
	private final OcpiChargingProfilesReceiverModule chargingProfilesModule;


	// tokens
	
	private final OcpiTokensReceiverModule tokensModule;
	private final CpoTokens tokens;
	private final Map<IdTokenEnum,Tuple2<Whitelist,TokenType>> tokenTypeInfos = 
			new HashMap<IdTokenEnum,Tuple2<Whitelist,TokenType>>();

	// commands

	private OcpiCommandsReceiverModule commandsModule;
	Vector< Tuple2<OcpiStartSession,OcppRequestStartTransactionRequest> > remoteStartByOcppId = new Vector< Tuple2<OcpiStartSession,OcppRequestStartTransactionRequest> >();

	// reservation
	
	Vector< CpoTransaction > reservationByOcppId = new Vector< CpoTransaction >();
	Map< String /* OcppId */, CpoTransaction > reservationByOcpiId = 
			new HashMap< String, CpoTransaction>();

	/****************************************************************************
	 * constructors and main getters/setters
	 */
	
	private CPO( CpoConfig config ) {
		this.id = config.id;
		this.config = config;
		
		for( CpoChargingStationConfig t : this.config.charging_stations ) {
			locationByCsId.put( t.id, t.location );
		}
		
		this.locations = mapFactory.make( this.id+".locations", OcpiLocation.class );
		locationsReceiverModule = new OcpiLocationsReceiverModule( this );
		locationsSenderModule = new OcpiLocationsSenderModule( this );
		
		cdrsModule = new OcpiCDRsSenderModule( this );
		tariffsById = mapFactory.make( id+".tariffs", OcpiTariff.class );
		tariffsSenderModule = new OcpiTariffsSenderModule( this );
		tariffsReceiverModule = new OcpiTariffsReceiverModule( this );

		this.tokens = new CpoTokens( this );
		tokensModule = new OcpiTokensReceiverModule( this );
		
		sessionsModule = new OcpiSessionsSenderModule( this );		
		chargingProfilesModule = new OcpiChargingProfilesReceiverModule( this );
		
		ocppTransactions = mapFactory.make( id+".transactions", CpoTransaction.class );
	}

	private static CPO make( CpoConfig config ) {
		CPO cpo = new CPO( config );
		
//TODO:		cpo.tokensModule.queryTokens( CpoOcpiServlet.getLinks(), MainStartStop.getLastAlive() );

		if( cpo.config.token_config != null ) {
			for( CpoTokenConfig tokenConfig : cpo.config.token_config ) {
				cpo.setTokenTypeInfo( tokenConfig.idTokenEnum, tokenConfig.whitelist, tokenConfig.tokenType);
 			}
		}
		
		String uniqueId = "cpo::"+cpo.id.toString();
		ChargingStation.registerCSMS( uniqueId, cpo );
		CpoOcpiServlet.registerAgent( cpo );
		ServletOAM.registerAgent(uniqueId, cpo);
	
		return cpo;
	}
	
	public static CPO make( JsonNode jsonNode ) throws Exception {
		CpoConfig config = readerConfig.readValue( jsonNode );
		return make( config );
	}
	
	public static CPO getCPO( OcpiAgentId id ) {
		return (CPO) ChargingStation.getCSMS( "cpo::"+id.toString() );
	}
	
	@Override
	public OcpiAgentId getId() {
		return this.id;
	}
	
	Set<String /*csId*/ > getChargingStationIds() {
		return this.locationByCsId.keySet() ;
	}
	
	/****************************************************************************
	 * peers
	 */

	public void makeServiceLink( String ownToken, OcpiClientInfo.Role peerRole ) {
		OcpiLink link = new OcpiLink();
		link.ownId = getId();
		link.ownCredentials = new OcpiCredentials();
		link.ownCredentials.setUrl( OcpiConfig.getPublicURI().resolve( "/cso/ocpi/" ) );
		link.ownCredentials.setRoles( getServiceRoles() );
		link.ownCredentials.setToken( ownToken );
		CpoOcpiServlet.allowLink( link );
	}
	
	public void addEMSP( OcpiLink link ) {
		locationsSenderModule.addReceiver( link );
	}

	private OcpiLink getLink( OcpiAgentId agentId ) {
		return CpoOcpiServlet.getLink(agentId);
	}

	/****************************************************************************
	 * servlets & modules
	 */
	
	@Override
	public OcpiResult<?> executeRequest( OcpiRequestData oreq ) throws Exception {
		if( oreq.servlet instanceof CpoOcpiServlet ) {
			// Service request
			switch( oreq.module ) {
			case LOCATIONS: return locationsSenderModule.senderInterface( oreq );
			case SESSIONS: return sessionsModule.senderInterface( oreq );
			case CHARGINGPROFILES: return chargingProfilesModule.receiverInterface( oreq );
			case TOKENS: return tokensModule.receiverInterface( oreq );
			case TARIFFS: return tariffsSenderModule.senderInterface( oreq );
			case COMMANDS: return commandsModule.receiverInterface( oreq );
			default: return OcpiResultEnum.NOT_SUPPORTED_ENDPOINT;
		}
		} else {
			// O&M request
			switch( oreq.module ) {
				case LOCATIONS: return locationsReceiverModule.receiverInterface( oreq );
				case TOKENS: return tokensModule.receiverInterface( oreq );
				case TARIFFS: return tariffsReceiverModule.receiverInterface( oreq );
				default: return OcpiResultEnum.NOT_SUPPORTED_ENDPOINT;
			}
		}
	}

	public List<OcpiCredentialsRole> getServiceRoles() {
		OcpiCredentialsRole cpoRole = new OcpiCredentialsRole();
		cpoRole.setRole( OcpiClientInfo.Role.CPO ); 
		cpoRole.setCountryCode( id.countryCode );
		cpoRole.setPartyId( id.partyId );		
		return Collections.singletonList( cpoRole );
	}

	public List<OcpiCredentialsRole> getOAMRoles() {
		OcpiCredentialsRole cpoRole = new OcpiCredentialsRole();
		cpoRole.setRole( OcpiClientInfo.Role.OTHER ); 
		cpoRole.setCountryCode( id.countryCode );
		cpoRole.setPartyId( id.partyId );		
		return Collections.singletonList( cpoRole );
	}
	
	@Override
	public	URI getOcpiModuleUri(InterfaceRole role, Identifier module) {
		return CpoOcpiServlet.getEndpoint(module);
	}

	@Override
	public Iterator<? extends HasLastUpdated> getOcpiItems( OcpiRequestData oreq ) {
		switch( oreq.module ) {
		case CDRS: 
			return this.CDRs.get( oreq.from ).iterator();
			
		case CHARGINGPROFILES:
			break;
		case COMMANDS:
			break;
		case CREDENTIALS:
			break;
			
		case LOCATIONS: 
			return this.locations.iterator();
			
		case SESSIONS:
			return new Iterator<OcpiSession>() {
				// filter transactions by eMSP and return related session
				private final Iterator<CpoTransaction> iter = CPO.this.ocppTransactions.iterator();
				private OcpiSession ocpiSession = null;

				@Override
				public boolean hasNext() {
					if( ocpiSession != null ) return true;
					
					do {
						if( !iter.hasNext() ) return false;
						
						OcpiSession candidate = iter.next().session;
						
						if(  candidate != null
						  && oreq.from.countryCode.equals( candidate.getCountryCode() )
						  && oreq.from.partyId.equals( candidate.getPartyId() )) {
							ocpiSession = candidate;
							return true;
						}

					} while( true );
				}

				@Override
				public OcpiSession next() {
					if( !this.hasNext() ) throw new NoSuchElementException();
					OcpiSession res = ocpiSession;
					ocpiSession = null;
					return res;
				}
			};
			
		case TARIFFS:
			return this.tariffsById.iterator();
			
		case TOKENS:
			break;
		}
		
		throw new IllegalArgumentException();
	}
	



	/****************************************************************************
	 * locations
	 */

	/*
	 *  Charging station *<=>1 CPO
	 *  Charging station 1<=>* EVSE
	 *  
	 *  CPO 1<=>* Location
	 *  Location 1<=>* EVSE
	 */
	
	/*
	 * EVSE unique identifiers:
	 * - CPO + ocpi location id + ocpi evse id
	 * - Charging station + ocpp evse idx 
	 */
	

	@Override
	public OcpiLocation getOcpiLocation( String locationId ) {
		return locations.get( locationId );
	}


	@Override
	public void updateLocation(OcpiLocation location, OcpiLocation delta) {
		locations.put( location.getId(),  location );
	}

	@Override
	public void updateEvse(OcpiLocation location, OcpiEvse evse, OcpiEvse delta) {
		locations.put( location.getId(),  location );
	}

	@Override
	public void updateConnector(OcpiLocation location, OcpiEvse evse, OcpiConnector connector, OcpiConnector delta) {
		locations.put( location.getId(),  location );
	}


	Tuple2<OcpiLocation,OcpiEvse> getEvse( ChargingStation cs, int evseIdx ) {
		String locationId = locationByCsId.get( cs.id );
		OcpiLocation location = locations.get(locationId);
		
		// in OCPP, index starts by 1
		if( location.getEvses() == null ) return null;
		OcpiEvse evse = location.getEvses().get(evseIdx-1);

		return new Tuple2<OcpiLocation,OcpiEvse> ( location, evse );
	}

	@Override
	public void handleStatusNotification( ChargingStation cs, OcppStatusNotificationRequest request ) {
		Tuple2<OcpiLocation, OcpiEvse> t = getEvse( cs, request.getEvseId() );
		if( t == null ) return;
		OcpiLocation location = t.f1;
		OcpiEvse evse = t.f2;

		OcpiConnector connector = OcpiLocationsReceiverModule.getConnectorByOcppIdx( evse, request.getConnectorId() );
		if( connector == null ) return;

		OcpiEvse evseUpdate = new OcpiEvse();

		switch( request.getConnectorStatus() ) {
		case OCCUPIED: 
			evseUpdate.setStatus( EvseStatus.CHARGING ); // operative and busy
			break;
			
		case RESERVED: 
			evseUpdate.setStatus( EvseStatus.RESERVED ); // operative but reserved
			break;
			
		case FAULTED: 
			evseUpdate.setStatus( EvseStatus.OUTOFORDER ); // The EVSE/Connector is currently out of order, some part/components may be broken/defect.
			break;
			
		case UNAVAILABLE:
			evseUpdate.setStatus( EvseStatus.INOPERATIVE ); // The EVSE/Connector is not yet active, or temporarily not available for use, but not broken or defect.
			break;
			
		case AVAILABLE: 
			evseUpdate.setStatus( EvseStatus.AVAILABLE ); // The EVSE/Connector is able to start a new charging session.
			break;
		}
		
		locationsSenderModule.reportEvseChange( location.getId(), evseUpdate );
	}

	/****************************************************************************
	 * TOKENS
	 */
	

	@Override
	public OcpiToken getToken(TokenType tokenType, String tokenId) {
		Tuple2<TokenType,String> key = new Tuple2<TokenType,String>(tokenType,tokenId);
		return this.tokens.get( key );
	}

	@Override
	public void updateToken(OcpiToken token) {
		Tuple2<TokenType,String> key = new Tuple2<TokenType,String>( token.getType(), token.getUid() );
		this.tokens.put( key, token );
	}

	@Override
	public void removeToken(TokenType tokenType, String tokenId) {
		Tuple2<TokenType,String> key = new Tuple2<TokenType,String>(tokenType,tokenId);
		this.tokens.remove( key );
	}

	public void setTokenTypeInfo( IdTokenEnum idTokenEnum, Whitelist whitelist, TokenType tokenType ) {
		tokenTypeInfos.put(idTokenEnum, new Tuple2<Whitelist,TokenType>(whitelist,tokenType));
	}

	Tuple2<OcpiAuthorizationInfo, AuthMethod> authorize( IdTokenEnum ocppTokenType, String tokenId ) throws Exception {
		Log.debug( "CPO.authorize: tokenType=%s tokenId=%s", ocppTokenType, tokenId );
		
		Tuple2<Whitelist,TokenType> tti = tokenTypeInfos.get( ocppTokenType );
		if( tti == null ) {
			OcpiAuthorizationInfo authorizationInfo = new OcpiAuthorizationInfo(); 
			authorizationInfo.setAllowed( Allowed.NOT_ALLOWED );
			return new Tuple2<OcpiAuthorizationInfo, AuthMethod>( authorizationInfo, AuthMethod.WHITELIST );
		} 
		Whitelist whitelist = tti.f1;
		TokenType ocpiTokenType = tti.f2;
		
		if( ocppTokenType == IdTokenEnum.NO_AUTHORIZATION && whitelist != Whitelist.NEVER ) {
			OcpiAuthorizationInfo authorizationInfo = new OcpiAuthorizationInfo(); 
			authorizationInfo.setAllowed( Allowed.ALLOWED );
			return new Tuple2<OcpiAuthorizationInfo, AuthMethod>( authorizationInfo, AuthMethod.WHITELIST );
		}

		return authorize( whitelist, ocpiTokenType, tokenId );
	}

	private Tuple2<OcpiAuthorizationInfo,AuthMethod> authorize( 
			Whitelist whitelist, TokenType ocpiTokenType, String tokenId ) throws Exception {
		OcpiAuthorizationInfo authorizationInfo = null; 

		OcpiToken ocpiToken = this.getToken( ocpiTokenType, tokenId );

		if ( ocpiToken != null ) whitelist = ocpiToken.getWhitelist();
		if( whitelist == Whitelist.ALLOWED ) whitelist = this.config.allowed_whitelist ; 
		
		OcpiAgentId eMSP = ( ocpiToken == null ? 
								this.config.default_EMSP : 
								new OcpiAgentId( ocpiToken.getCountryCode(), ocpiToken.getPartyId() )); 

		if(  whitelist != Whitelist.ALWAYS ) {
			OcpiLink link = getLink( eMSP );

			try {
				authorizationInfo = link.makeBuilder()
						.uri( Identifier.TOKENS )
						.parameter( tokenId )
						.parameter( "authorize" )
						.method​( HttpMethod.POST, null)
						.query( "type", ocpiTokenType.name() )
						.send( OcpiAuthorizationInfo.class );
			} catch ( Exception e ) {
				Log.error( e );
				// ignore
			}

			if( authorizationInfo != null ) {
				// post successful
				return new Tuple2<OcpiAuthorizationInfo,AuthMethod>( authorizationInfo, AuthMethod.AUTH_REQUEST );
			}
			
			// post failed, try offline if allowed
			if( whitelist == Whitelist.NEVER ) {
				throw new OcppException( CpoError.eMSPUnreachable ); 
			}
		}

		// OcpiToken => OcpiAuthorizationInfo
		authorizationInfo = new OcpiAuthorizationInfo();
		authorizationInfo.setAllowed( ocpiToken != null && ocpiToken.getValid() ? Allowed.ALLOWED : Allowed.NOT_ALLOWED );
		authorizationInfo.setToken( ocpiToken );
		return new Tuple2<OcpiAuthorizationInfo,AuthMethod>( authorizationInfo, AuthMethod.WHITELIST );
	}


	@Override
	public OcppIdTokenInfo authorize( ChargingStation cs, OcppIdToken idToken ) throws Exception {
		IdTokenEnum ocppTokenType = idToken.getType();
		String tokenId = idToken.getIdToken();
		
		Tuple2<OcpiAuthorizationInfo, AuthMethod> auth2 = authorize( ocppTokenType, tokenId );
		OcpiAuthorizationInfo authorizationInfo = auth2.f1;
		
		OcppIdTokenInfo res = new OcppIdTokenInfo(); 
		res.setStatus( OcpiTypeTools.toAuthorizationStatusEnum( authorizationInfo.getAllowed() ));
		return res;
	}
	
	void localToken( OcpiToken ocpiToken, OcppAuthorizationData ocppAuthorizationData ) {
		for( String csId : getChargingStationIds() ) {
			ChargingStation cs = (ChargingStation)OcppEndpoint.getAgent(csId);
			
			if( ocpiToken.getValid() ) {
				cs.sendLocalToken( ocppAuthorizationData );
			} else {
				cs.removeLocalToken( ocppAuthorizationData );
			}
		}
	}

	
	/****************************************************************************
	 * Transactions & Sessions
	 */

	private void initTransactionsByCsAndId() {
		transactionsByCsAndId =	new HashMap<Tuple2<String,String>,CpoTransaction>();

		for( CpoTransaction transaction : this.ocppTransactions ) {
			this.initTransaction(transaction);
			
			Tuple2<String,String> key = new Tuple2<String,String>( transaction.cs.id, transaction.transactionId );
			transactionsByCsAndId.put( key,  transaction );
		}
	}

	private void initTransaction( CpoTransaction transaction ) {
		transaction.cpo = this;
		transaction.cs = ChargingStation.getChargingStation( transaction.csId );
	}
	
	private CpoTransaction getTransactionBySessionId( String sessionId ) {
		CpoTransaction res = this.getTransactionBySessionId( sessionId );
		if( res == null ) return null;
		
		this.initTransaction( res );
		return res;
	}

	@Override
	public CpoTransaction getTransactionById( String transactionId, ChargingStation cs, Integer ocppReservationId ) {
		if( transactionsByCsAndId == null ) {
			initTransactionsByCsAndId();
		}
		
		Tuple2<String,String> key = new Tuple2<String,String>( cs.id, transactionId );

		CpoTransaction transaction = this.transactionsByCsAndId.get( key );
		if( transaction != null ) {
			return transaction;
		}
		
		if( ocppReservationId == null ) {
			transaction = new CpoTransaction();
			
			transaction.csId = cs.id;
			this.initTransaction( transaction );

			transaction.session = new OcpiSession();
			transaction.session.setCountryCode( this.id.countryCode );
			transaction.session.setPartyId( this.id.partyId );
			transaction.session.setId( UUID.randomUUID().toString() );
			
		} else {
			transaction = this.reservationByOcppId.get( ocppReservationId );
			this.reservationByOcpiId.remove( transaction.ocpiReservationId );
			this.reservationByOcppId.set( transaction.ocppReservationId, null );
			
		}
		
		transaction.transactionId = transactionId;

		updateTransaction( transaction );

		return transaction;
	}
	
	void updateTransaction( CpoTransaction transaction ) {
		this.ocppTransactions.put( transaction.session.getId(), transaction );
		this.transactionsByCsAndId.put( new Tuple2<String,String>( transaction.csId, transaction.transactionId), transaction );

		if( transaction.eMSP != null ) {
			OcpiLink link = getLink( transaction.eMSP );
			sessionsModule.reportSessionChange( link, transaction.session );
		}
	}
	
	public void removeTransaction(CpoTransaction transaction) {
		this.ocppTransactions.remove( transaction.session.getId() );
		Tuple2<String,String> key = new Tuple2<String,String>( transaction.cs.id, transaction.transactionId );
		transactionsByCsAndId.remove( key,  transaction );
	}

	@Override
	public OcpiCommandResponse handleStartSessionCommand(OcpiAgentId eMSP, OcpiStartSession command) {
		OcpiCommandResponse response = new OcpiCommandResponse();
		response.setResult( OcpiCommandResponse.Result.REJECTED );
		response.setTimeout( 300 );

		String locationId = command.getLocationId();
		OcpiLocation location = locations.get( locationId );
		if( location == null ) return response;

		OcppRequestStartTransactionRequest ocppRequestStartTransactionRequest = new OcppRequestStartTransactionRequest();
		ocppRequestStartTransactionRequest.setChargingProfile(null); // optional

		// select EVSE

		String evseId = command.getEvseUid();
		if( evseId == null ) {
			for( OcpiEvse evse : location.getEvses() ) {
				if( evse.getStatus() == EvseStatus.AVAILABLE ) {
					evseId = evse.getEvseId();
				}
			}
		}

		Tuple2<ChargingStation, Integer> cs_evseIdx = ocppCSEvses.get( new Tuple2<String,String>( locationId, evseId ));
		ChargingStation cs = cs_evseIdx.f1;

		ocppRequestStartTransactionRequest.setEvseId( cs_evseIdx.f2 ); // optional

		// Token

		OcpiToken ocpiToken = command.getToken();
		if( ocpiToken == null ) return response;
		
		OcppAuthorizationData ocppAuthorizationData = OcpiTypeTools.toOcppAuthorizationData( ocpiToken );
		ocppRequestStartTransactionRequest.setIdToken( ocppAuthorizationData.getIdToken() );
		ocppRequestStartTransactionRequest.setGroupIdToken( ocppAuthorizationData.getIdTokenInfo().getGroupIdToken() );

		// generate ocppId and store command

		Tuple2<OcpiStartSession,OcppRequestStartTransactionRequest> data = 
				new Tuple2<OcpiStartSession,OcppRequestStartTransactionRequest>(command,ocppRequestStartTransactionRequest);
		int tmpId = -1;
		synchronized( remoteStartByOcppId ) {
			tmpId = remoteStartByOcppId.indexOf(null);
			if( tmpId == -1 ) {
				tmpId = remoteStartByOcppId.size(); 
				remoteStartByOcppId.add( data );
			} else {
				remoteStartByOcppId.set( tmpId, data );
			}
		}
		final int ocppId = tmpId;
		ocppRequestStartTransactionRequest.setRemoteStartId( ocppId );

		// send command and handle answer

		cs.send( ocppRequestStartTransactionRequest, (ocppCommand)-> {
			OcpiCommandResult ocpiCommandResult = new OcpiCommandResult();
			boolean error = true;

			if( ocppCommand.error != null ) {
				ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

			} else {
				OcppRequestStartTransactionResponse ocppRequestStartTransactionResponse = (OcppRequestStartTransactionResponse) ocppCommand.answer;

				if( ocppRequestStartTransactionResponse == null ) {
					ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

				} else {

					switch( ocppRequestStartTransactionResponse.getStatus() ) {
					case ACCEPTED:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.ACCEPTED );
						error = false;

						String transactionId = ocppRequestStartTransactionResponse.getTransactionId();
						if( transactionId != null ) {
							CpoTransaction transaction = getTransactionById(transactionId,null,null);
							transaction.storeStartSessionCommandData( ocppId );
						}

						break;

					case REJECTED:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.REJECTED );
						break;
					}
				}
			}

			if( error ) {
				remoteStartByOcppId.set( ocppId, null );
			}

			OcpiLink link = getLink(eMSP);

			try {
				link.makeBuilder()
				.uri( command.getResponseUrl() ) 
				.method​( HttpMethod.POST, ocpiCommandResult )
				.send();

			} catch (Exception e) {
				Log.error( e );
			}
		} );

		response.setResult( OcpiCommandResponse.Result.ACCEPTED );
		return response;
	}

	@Override
	public OcpiCommandResponse handleStopSessionCommand(OcpiAgentId eMSP, OcpiStopSession command) {
		OcpiCommandResponse response = new OcpiCommandResponse();
		response.setResult( OcpiCommandResponse.Result.UNKNOWN_SESSION );
		response.setTimeout( 300 );

		String sessionId = command.getSessionId();
		CpoTransaction transaction = this.getTransactionBySessionId( sessionId );
		if( transaction == null ) return response;

		OcppRequestStopTransactionRequest ocppRequestStopTransactionRequest = new OcppRequestStopTransactionRequest();
		ocppRequestStopTransactionRequest.setTransactionId( transaction.transactionId );

		transaction.cs.send( ocppRequestStopTransactionRequest, (ocppCommand)-> {
			OcpiCommandResult ocpiCommandResult = new OcpiCommandResult();

			if( ocppCommand.error != null ) {
				ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

			} else {
				OcppRequestStopTransactionRequest ocppRequestStopTransactionResponse = (OcppRequestStopTransactionRequest) ocppCommand.answer;

				if( ocppRequestStopTransactionResponse == null ) {
					ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

				} else {
					ocpiCommandResult.setResult( OcpiCommandResult.Result.ACCEPTED );

					//					String transactionId = ocppRequestStopTransactionResponse.getTransactionId();
				}
			}

			OcpiLink link = getLink(eMSP);

			try {
				link.makeBuilder()
				.uri( command.getResponseUrl() ) 
				.method​( HttpMethod.POST, ocpiCommandResult )
				.send();

			} catch (Exception e) {
				Log.error( e );
			}
		} );

		response.setResult( OcpiCommandResponse.Result.ACCEPTED );
		return response;
	}

	@Override
	public OcpiCommandResponse handleUnlockConnectorCommand(OcpiAgentId eMSP, OcpiUnlockConnector command) {
		OcpiCommandResponse response = new OcpiCommandResponse();
		response.setResult( OcpiCommandResponse.Result.REJECTED );
		response.setTimeout( 300 );

		String locationId = command.getLocationId();
		OcpiLocation location = locations.get( locationId );
		if( location == null ) return response;

		OcppUnlockConnectorRequest ocppUnlockConnectorRequest = new OcppUnlockConnectorRequest();
		// select EVSE

		String evseId = command.getEvseUid();
		Tuple2<ChargingStation, Integer> cs_evseIdx = ocppCSEvses.get( new Tuple2<String,String>( locationId, evseId ));
		ChargingStation cs = cs_evseIdx.f1;
		ocppUnlockConnectorRequest.setEvseId( cs_evseIdx.f2 ); 

		OcpiEvse evse = OcpiLocationsReceiverModule.getEvseByOcpiId( location, evseId ); 
		Tuple2<OcpiConnector, Integer> tConnector = OcpiLocationsReceiverModule.getConnectorByOcpiId( evse, command.getConnectorId() ); 
		ocppUnlockConnectorRequest.setConnectorId( tConnector.f2 ); 

		// send command and handle answer

		cs.send( ocppUnlockConnectorRequest, (ocppCommand)-> {
			OcpiCommandResult ocpiCommandResult = new OcpiCommandResult();

			if( ocppCommand.error != null ) {
				ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

			} else {
				OcppUnlockConnectorResponse ocppUnlockConnectorResponse = (OcppUnlockConnectorResponse) ocppCommand.answer;

				if( ocppUnlockConnectorResponse == null ) {
					ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

				} else {

					switch( ocppUnlockConnectorResponse.getStatus() ) {
					case UNLOCKED:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.ACCEPTED );
						break;
					case ONGOING_AUTHORIZED_TRANSACTION:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.REJECTED );
						break;
					case UNKNOWN_CONNECTOR:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );
						break;
					case UNLOCK_FAILED:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );
						break;
					}
				}
			}

			OcpiLink link = getLink(eMSP);

			try {
				link.makeBuilder()
				.uri( command.getResponseUrl() ) 
				.method​( HttpMethod.POST, ocpiCommandResult )
				.send();

			} catch (Exception e) {
				Log.error( e );
			}
		} );

		response.setResult( OcpiCommandResponse.Result.ACCEPTED );
		return response;
	}

	
	/******************************************************************************
	 * Reservation
	 */
	
	@Override
	public OcpiCommandResponse handleReserveNowCommand( OcpiAgentId eMSP, OcpiReserveNow command ) {

		OcpiCommandResponse response = new OcpiCommandResponse();
		response.setResult( OcpiCommandResponse.Result.REJECTED );
		response.setTimeout( 300 );

		String locationId = command.getLocationId();
		OcpiLocation location = locations.get( locationId );
		if( location == null ) return response;

		OcppReserveNowRequest ocppReserveNowRequest = new OcppReserveNowRequest();
		ocppReserveNowRequest.setExpiryDateTime( command.getExpiryDate() );

		// new Session

		final OcpiSession ocpiSession = new OcpiSession();
		ocpiSession.setCountryCode( this.id.countryCode );
		ocpiSession.setPartyId( this.id.partyId );
		ocpiSession.setId( UUID.randomUUID().toString() );

		ocpiSession.setStartDatetime( Instant.now() );
		ocpiSession.setStatus( OcpiSession.Status.RESERVATION );
		ocpiSession.setAuthMethod( AuthMethod.COMMAND );
		ocpiSession.setAuthReference( command.getAuthorizationReference() );
		ocpiSession.setLastUpdated( ocpiSession.getStartDatetime() );

		// select EVSE

		String evseId = command.getEvseUid();
		if( evseId == null ) {
			for( OcpiEvse evse : location.getEvses() ) {
				if( evse.getStatus() == EvseStatus.AVAILABLE ) {
					evseId = evse.getEvseId();
				}
			}
		}

		ocpiSession.setLocationId( location.getId() );
		ocpiSession.setEvseUid( evseId );
		ocpiSession.setConnectorId( "#NA" );
		ocppReserveNowRequest.setConnectorType( null );

		Tuple2<ChargingStation, Integer> cs_evseIdx = ocppCSEvses.get( new Tuple2<String,String>( locationId, evseId ));
		ChargingStation cs = cs_evseIdx.f1;

		ocppReserveNowRequest.setEvseId( cs_evseIdx.f2 );

		// Token

		OcpiToken ocpiToken = command.getToken();
		if( ocpiToken == null ) return response;
		
		OcppAuthorizationData ocppAuthorizationData = OcpiTypeTools.toOcppAuthorizationData( ocpiToken );
		ocppReserveNowRequest.setIdToken( ocppAuthorizationData.getIdToken() );
		ocppReserveNowRequest.setGroupIdToken( ocppAuthorizationData.getIdTokenInfo().getGroupIdToken() );

		// store new reservation
		CpoTransaction transaction = new CpoTransaction();
		transaction.ocpiReservationId = command.getReservationId();
		transaction.session = ocpiSession;
		transaction.ocppReservationId = -1;
		synchronized( reservationByOcppId ) {
			transaction.ocppReservationId = reservationByOcppId.indexOf(null);
			if( transaction.ocppReservationId == -1 ) {
				transaction.ocppReservationId = reservationByOcppId.size(); 
				reservationByOcppId.add( transaction );
			} else {
				reservationByOcppId.set( transaction.ocppReservationId, transaction );
			}
		}
		reservationByOcpiId.put( transaction.ocpiReservationId, transaction );
		ocppReserveNowRequest.setId( transaction.ocppReservationId );

		// send command and handle answer

		cs.send( ocppReserveNowRequest, (ocppCommand)-> {
			OcpiCommandResult ocpiCommandResult = new OcpiCommandResult();
			boolean error = true;

			if( ocppCommand.error != null ) {
				ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

			} else {
				OcppReserveNowResponse ocppReserveNowResponse = (OcppReserveNowResponse) ocppCommand.answer;

				if( ocppReserveNowResponse == null ) {
					ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

				} else {
					switch( ocppReserveNowResponse.getStatus() ) {
					case ACCEPTED:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.ACCEPTED );
						error = false;
						break;

					case FAULTED:
					case UNAVAILABLE:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.EVSE_INOPERATIVE_EVSE );
						break;

					case OCCUPIED:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.EVSE_OCCUPIED_EVSE );
						break;

					case REJECTED:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.REJECTED );
						break;
					}}
			}

			if( error ) {
				reservationByOcppId.set( transaction.ocppReservationId, null );
				reservationByOcpiId.remove( transaction.ocpiReservationId );
			} else {
				OcpiLink link = getLink(eMSP);
				this.sessionsModule.reportSessionChange( link, ocpiSession );
			}

			OcpiLink link = getLink(eMSP);
			try {
				link.makeBuilder()
				.uri( command.getResponseUrl() ) 
				.method​( HttpMethod.POST, ocpiCommandResult )
				.send();

			} catch (Exception e) {
				Log.error( e );
			}



		} );

		response.setResult( OcpiCommandResponse.Result.ACCEPTED );
		return response;
	}

	@Override
	public void reservationFinished(  ChargingStation cs, Integer ocppReservationId, boolean removed ) {
		CpoTransaction transaction = this.reservationByOcppId.get( ocppReservationId );
		reservationByOcpiId.remove( transaction.ocpiReservationId );
		reservationByOcppId.set( transaction.ocppReservationId, null );
		
		if( removed ) {
			transaction.doAbort();
			
		} else {
			transaction.doFinish();
			
		}
	}

	@Override
	public OcpiCommandResponse handleCancelReservationCommand( 
			OcpiAgentId eMSP, OcpiCancelReservation command ) {

		CpoTransaction transaction = reservationByOcpiId.remove( command.getReservationId() );
		reservationByOcppId.set( transaction.ocppReservationId, null );

		OcppCancelReservationRequest ocppCancelReservationRequest = new OcppCancelReservationRequest();
		ocppCancelReservationRequest.setReservationId( transaction.ocppReservationId );

		transaction.cs.send( ocppCancelReservationRequest, (ocppCommand)-> {
			OcpiCommandResult ocpiCommandResult = new OcpiCommandResult();

			if( ocppCommand.error != null ) {
				ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

			} else {
				OcppCancelReservationResponse ocppCancelReservationResponse = (OcppCancelReservationResponse) ocppCommand.answer;

				if( ocppCancelReservationResponse == null ) {
					ocpiCommandResult.setResult( OcpiCommandResult.Result.FAILED );

				} else {

					switch( ocppCancelReservationResponse.getStatus() ) {
					case ACCEPTED:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.ACCEPTED );
						break;

					case REJECTED:
						ocpiCommandResult.setResult( OcpiCommandResult.Result.REJECTED );
						break;
					}
				}}

			OcpiLink link = getLink(eMSP);

			try {
				link.makeBuilder()
				.uri( command.getResponseUrl() ) 
				.method​( HttpMethod.POST, ocpiCommandResult )
				.send();

			} catch (Exception e) {
				Log.error( e );
			}

		} );

		OcpiCommandResponse response = new OcpiCommandResponse();
		response.setResult( OcpiCommandResponse.Result.ACCEPTED );
		response.setTimeout( 300 );
		return response;
	}


	/****************************************************************************
	 * Tariffs, CDRs & ChargingProfiles
	 */
	

	@Override
	public OcpiTariff getTariff(String tariffId) {
		return this.tariffsById.get(tariffId);
	}

	@Override
	public void updateTariff(OcpiTariff tariff, OcpiTariff delta) {
		this.tariffsById.put( tariff.getId(), tariff );
	}

	@Override
	public void removeTariff(String tariffId) {
		OcpiTariff tariff = this.tariffsById.get(tariffId);
		if( tariff == null ) return;
		
		this.tariffsById.remove(tariffId);
	}


	public List<OcpiTariff> getApplicableTariffs(CpoTransaction cpoTransaction) {
		List<OcpiTariff> res = new LinkedList<OcpiTariff>();
		
		OcpiTariff.Type tariffType = OcpiTariff.Type.REGULAR;
		if( cpoTransaction.chargingPreferences != null && cpoTransaction.chargingPreferences.getProfileType() != null ) {
			switch( cpoTransaction.chargingPreferences.getProfileType() ) {
			case CHEAP:
				tariffType = OcpiTariff.Type.PROFILE_CHEAP;
				break;
			case FAST:
				tariffType = OcpiTariff.Type.PROFILE_FAST;
				break;
			case GREEN:
				tariffType = OcpiTariff.Type.PROFILE_GREEN;
				break;
			case REGULAR:
				tariffType = OcpiTariff.Type.REGULAR;
				break;
			}
		}
		
		for( OcpiTariff tariff : tariffsById ) {
			Instant now = Instant.now();
			if( tariff.getStartDateTime() != null && now.isBefore( tariff.getStartDateTime() ) ) continue;
			if( tariff.getEndDateTime() != null && now.isAfter( tariff.getEndDateTime() ) ) continue;
			if( tariff.getType() != tariffType ) continue;
			
			res.add( tariff );
		}
		
		return res;
	}


	private SimpleMap<String, OcpiCdr> getCDRMap( OcpiAgentId eMSP ) {
		synchronized(CDRs) {
			SimpleMap<String, OcpiCdr> map = CDRs.get( eMSP );
			if( map == null ) {
				map = mapFactory.make( id+".CDRs."+eMSP, OcpiCdr.class );
				CDRs.put( eMSP, map );
			}
			return map;
		}
	}
	
	

	String /*cdr.id*/ addCDR( OcpiCdr cdr ) {
		OcpiCdrToken token = cdr.getCdrToken();
		if( token == null || token.getCountryCode() == null || token.getPartyId() == null ) return null;
		OcpiAgentId eMSP = new OcpiAgentId( token.getCountryCode(), token.getPartyId() );

		SimpleMap<String, OcpiCdr> map = getCDRMap( eMSP );
		
		String id;
		synchronized( map ) {
			do {
				id = UUID.randomUUID().toString();
			} while( map.get(id) != null );
			cdr.setId(id);
			map.put( id, cdr );
		}

		OcpiLink link = this.getLink(eMSP);
		if( link != null ) cdrsModule.reportChange( link, cdr );
		return id;
	}

	@Override
	public OcpiResult<?> setChargingPreferences(
			String ocpiSessionId,
			OcpiChargingPreferences chargingPreferences) {
		// called from sessions sender module
		
		CpoTransaction transaction = this.getTransactionBySessionId( ocpiSessionId );
		if( transaction == null ) {
			return OcpiResultEnum.UNKNOWN_ITEM;
		}
		
		return transaction.setChargingPreferences( chargingPreferences );
	}
	
	@Override
	public OcpiResult<?> handleGetChargingProfile(
			OcpiAgentId SCSP, URI responseUrl, 
			String ocpiSessionId,
			Integer duration ) {
		// called from Charging Profiles Module
		
		OcpiChargingProfileResponse chargingProfileResponse = new OcpiChargingProfileResponse();
		chargingProfileResponse.setTimeout( 0.0 );
		
		CpoTransaction transaction = getTransactionBySessionId( ocpiSessionId );
		if( transaction == null ) {
			chargingProfileResponse.setResult( ChargingProfileResponseType.UNKNOWN_SESSION );
			return OcpiResult.success( chargingProfileResponse );
		}
		
		transaction.getCompositeSchedule( duration, (command)-> {
			OcppGetCompositeScheduleResponse ocppGetCompositeScheduleResponse = (OcppGetCompositeScheduleResponse) command.answer;
		
			try {
				OcpiActiveChargingProfileResult.Result ocprr =
						ocppGetCompositeScheduleResponse == null ? OcpiActiveChargingProfileResult.Result.REJECTED
					  : ocppGetCompositeScheduleResponse.getStatus() == GenericStatusEnum.ACCEPTED ? OcpiActiveChargingProfileResult.Result.ACCEPTED
					  : OcpiActiveChargingProfileResult.Result.REJECTED;

				OcppCompositeSchedule ocppCompositeSchedule = ocppGetCompositeScheduleResponse.getSchedule();
				OcpiActiveChargingProfile ocpiActiveChargingProfile = OcpiTypeTools.fromOcppCompositeSchedule( ocppCompositeSchedule );

				OcpiActiveChargingProfileResult ocpiActiveChargingProfileResult = new OcpiActiveChargingProfileResult(); 
				ocpiActiveChargingProfileResult.setResult( ocprr ); 
				ocpiActiveChargingProfileResult.setActiveChargingProfile( ocpiActiveChargingProfile );
				
				OcpiLink link = getLink(SCSP);

				link.makeBuilder()
					.uri( responseUrl ) 
					.method​( HttpMethod.POST, ocpiActiveChargingProfileResult)
					.send();
				
			} catch (Exception e) {
				Log.error( e );
			}
		} );
		
		chargingProfileResponse.setResult( ChargingProfileResponseType.ACCEPTED );
		chargingProfileResponse.setTimeout( 300.0 );
		return OcpiResult.success( chargingProfileResponse );
	}
	
	@Override
	public OcpiResult<?> handleSetChargingProfile(
			OcpiAgentId SCSP, URI responseUrl, 
			String ocpiSessionId,
			OcpiChargingProfile chargingProfile) {
		// called from Charging Profiles Module
		
		OcpiChargingProfileResponse chargingProfileResponse = new OcpiChargingProfileResponse();
		chargingProfileResponse.setTimeout( 0.0 );
		
		CpoTransaction transaction = getTransactionBySessionId( ocpiSessionId );
		if( transaction == null ) {
			chargingProfileResponse.setResult( ChargingProfileResponseType.UNKNOWN_SESSION );
			return OcpiResult.success( chargingProfileResponse );
		}
		
		OcppChargingProfile ocppChargingProfile = OcpiTypeTools.toOcppChargingProfile( chargingProfile );

		transaction.setChargingProfile( ocppChargingProfile, (ocppCommand)-> {
			OcppSetChargingProfileResponse ocppSetChargingProfileResponse = null;
			if( ocppCommand.error == null ) {
				ocppSetChargingProfileResponse = (OcppSetChargingProfileResponse) ocppCommand.answer;
			}
		
			try {
				OcpiChargingProfileResult.Result ocprr =
						ocppSetChargingProfileResponse == null ? OcpiChargingProfileResult.Result.REJECTED
					  : ocppSetChargingProfileResponse.getStatus() == ChargingProfileStatusEnum.ACCEPTED ? OcpiChargingProfileResult.Result.ACCEPTED
					  : OcpiChargingProfileResult.Result.REJECTED;

				OcpiChargingProfileResult chargingProfileResult = new OcpiChargingProfileResult(); 
				chargingProfileResult.setResult( ocprr ); 

				OcpiLink link = getLink(SCSP);

				link.makeBuilder()
					.uri( responseUrl ) 
					.method​( HttpMethod.POST, chargingProfileResult)
					.send();
				
			} catch (Exception e) {
				Log.error( e );
			}
		} );
		
		chargingProfileResponse.setResult( ChargingProfileResponseType.ACCEPTED );
		chargingProfileResponse.setTimeout( 300.0 );
		return OcpiResult.success( chargingProfileResponse );
	}
	
	@Override
	public OcpiResult<?> handleDeleteChargingProfile(
			OcpiAgentId SCSP, URI responseUrl, 
			String ocpiSessionId) {
		// called from Charging Profiles Module
		
		OcpiChargingProfileResponse chargingProfileResponse = new OcpiChargingProfileResponse();
		chargingProfileResponse.setTimeout( 0.0 );
		
		CpoTransaction transaction = getTransactionBySessionId( ocpiSessionId );
		if( transaction == null ) {
			chargingProfileResponse.setResult( ChargingProfileResponseType.UNKNOWN_SESSION );
			return OcpiResult.success( chargingProfileResponse );
		}
		
		boolean deleteResult = transaction.deleteChargingProfile( (ocppCommand)-> {
			OcppSetChargingProfileResponse ocppSetChargingProfileResponse = null;
			if( ocppCommand.error == null ) {
					ocppSetChargingProfileResponse = (OcppSetChargingProfileResponse) ocppCommand.answer;
			}
		
			try {
				OcpiChargingProfileResult.Result ocprr =
						ocppSetChargingProfileResponse == null ? OcpiChargingProfileResult.Result.REJECTED
					  : ocppSetChargingProfileResponse.getStatus() == ChargingProfileStatusEnum.ACCEPTED ? OcpiChargingProfileResult.Result.ACCEPTED
					  : OcpiChargingProfileResult.Result.REJECTED;

				OcpiChargingProfileResult chargingProfileResult = new OcpiChargingProfileResult(); 
				chargingProfileResult.setResult( ocprr ); 

				OcpiLink link = getLink(SCSP);

				link.makeBuilder()
					.uri( responseUrl ) 
					.method​( HttpMethod.POST, chargingProfileResult)
					.send();
				
			} catch (Exception e) {
				Log.error( e );
			}
		} );
		
		chargingProfileResponse.setResult( 
				deleteResult ? ChargingProfileResponseType.ACCEPTED : ChargingProfileResponseType.REJECTED );
		chargingProfileResponse.setTimeout( 300.0 );
		return OcpiResult.success( chargingProfileResponse );
	}

	/*
	 * security
	 */
	
	@Override
	public boolean signChargingStationCSR( String csr ) {
		// TODO: forward CSR to the Certificate Authority and 
		// send a CertificateSignedRequest to the charging station 
		// with the new certificate
		
		return false;
	}

	/************************************************************************
	 * O&M
	 */
	
	private Object executeOAM( CpoOAMCommand command ) {
		switch( command.type ) {
		case "ocpiMakeLink": {
			OcpiMakeLinkCommand tcommand = (OcpiMakeLinkCommand)command;
			makeServiceLink( tcommand.ownToken, tcommand.peerRole );
			return 200;
		}
		
		default: return 404;
		}
	}
	
	@Override
	public void executeOAM(String[] uri, HttpServletRequest request, HttpServletResponse response) throws Exception {
		CpoOAMCommand command = readerOAM.readValue( request.getInputStream() );
		Object answer = executeOAM( command);
		ServletOAM.fillResponse( response, answer );
	}
}
