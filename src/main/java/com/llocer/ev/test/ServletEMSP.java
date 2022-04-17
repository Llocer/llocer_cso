package com.llocer.ev.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;

import javax.servlet.annotation.WebServlet;

import com.llocer.collections.SimpleMapFactory;
import com.llocer.common.Log;
import com.llocer.common.SimpleMap;
import com.llocer.ev.ocpi.modules.OcpiCredentialsModule;
import com.llocer.ev.ocpi.msgs22.OcpiAuthorizationInfo;
import com.llocer.ev.ocpi.msgs22.OcpiClientInfo;
import com.llocer.ev.ocpi.msgs22.OcpiCredentials;
import com.llocer.ev.ocpi.msgs22.OcpiCredentialsRole;
import com.llocer.ev.ocpi.msgs22.OcpiEndpoint;
import com.llocer.ev.ocpi.msgs22.OcpiEndpoints;
import com.llocer.ev.ocpi.msgs22.OcpiVersions;
import com.llocer.ev.ocpi.server.OcpiAgentId;
import com.llocer.ev.ocpi.server.OcpiConfig;
import com.llocer.ev.ocpi.server.OcpiLink;
import com.llocer.ev.ocpi.server.OcpiRequestData;
import com.llocer.ev.ocpi.server.OcpiResult;
import com.llocer.ev.ocpi.server.OcpiResult.OcpiResultEnum;
import com.llocer.ev.ocpi.server.OcpiServlet;

@WebServlet(value = "/emsp/ocpi/*") 
public class ServletEMSP extends OcpiServlet {
	private static final long serialVersionUID = -4764547316713059395L;
	private static final String servletPath = "emsp/ocpi";
	private static final String servletPath221 = servletPath+"/221/";

	private final static OcpiAgentId id = new OcpiAgentId( "US", "EMS" );
	private final static String token = "Token ATEST";

	/*
	 * versions and credentials
	 */

	@Override
	protected OcpiVersions[] getVersions() {
		OcpiVersions answer = new OcpiVersions();
		answer.setVersion( OcpiEndpoints.Version._2_2_1 );
		answer.setUrl( OcpiConfig.getPublicURI().resolve( "emsp/ocpi/221" ) );
		return new OcpiVersions[] { answer };
	}

	@Override
	protected OcpiEndpoints getEndpoints( String version ) {
		OcpiEndpoints answer = new OcpiEndpoints();
		answer.setVersion( OcpiEndpoints.Version._2_2_1 );
		answer.setEndpoints( new LinkedList<OcpiEndpoint>() );

		OcpiEndpoint endpoint;

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.CREDENTIALS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.SENDER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.CREDENTIALS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.RECEIVER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.LOCATIONS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.RECEIVER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.SESSIONS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.RECEIVER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.TARIFFS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.RECEIVER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.CDRS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.RECEIVER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.TOKENS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.SENDER );
		answer.getEndpoints().add( endpoint );

		for( OcpiEndpoint e : answer.getEndpoints() ) {
			e.setUrl( OcpiConfig.getPublicURI()
					.resolve( servletPath221+e.getIdentifier().toString()+"/" ));
		}
		
		return answer;
	}
	
	/*
	 * handlers
	 */
	
	@Override
	protected OcpiResult<?> executeModule( OcpiRequestData oreq ) throws Exception {
		if( oreq.request.getRequestURI().startsWith( "/emsp/ocpi/response" ) ) return OcpiResult.success(null);
		return super.executeModule( oreq );
	}

	@Override
	protected OcpiResult<?> execute( OcpiRequestData oreq ) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader( oreq.request.getInputStream()));
		String s;
		while ((s=br.readLine()) != null) {
		    //System.out.println(read);
		    Log.debug( "EMSP: request=%s", s );
		}
		br.close();
	    Log.dump( "EMSP: request=-----------" );
		
		switch( oreq.module ) {
		case LOCATIONS:
		case CDRS:
		case SESSIONS:
		case TARIFFS:
			return OcpiResultEnum.OK;
			
		case TOKENS: { //  .../{token_uid}/authorize[?type={type}]
			switch( oreq.method ) {
			case POST:
				OcpiAuthorizationInfo answer = new OcpiAuthorizationInfo();
				answer.setAllowed( OcpiAuthorizationInfo.Allowed.ALLOWED );
				return OcpiResult.success( answer );

			default:
				return OcpiResultEnum.METHOD_NOT_ALLOWED;
			}
		}
		
		default:
			return OcpiResultEnum.METHOD_NOT_ALLOWED;
		}
	}

	/*
	 * links
	 */
	
	private static final OcpiCredentialsModule ocpiCredentialsModule = initCredentials();

	private static OcpiCredentialsModule initCredentials() {
		SimpleMapFactory mapFactory = SimpleMapFactory.forName( "memory" );
		SimpleMap<String, OcpiLink> linksByToken = mapFactory.make( "credentials", OcpiLink.class);

		OcpiCredentialsModule ocpiCredentialsModule = new OcpiCredentialsModule(linksByToken); 
		
		OcpiLink link = new OcpiLink();
		link.ownId = id;
		link.ownCredentials = new OcpiCredentials();
		link.ownCredentials.setUrl( OcpiConfig.getPublicURI().resolve( servletPath ) );
		link.ownCredentials.setToken( token );
		
		OcpiCredentialsRole cpoRole = new OcpiCredentialsRole();
		cpoRole.setRole( OcpiClientInfo.Role.EMSP ); 
		cpoRole.setCountryCode( id.countryCode );
		cpoRole.setPartyId( id.partyId );		
		link.ownCredentials.setRoles( Collections.singletonList( cpoRole ) );
		
		ocpiCredentialsModule.allowLink( link );
		
		return ocpiCredentialsModule;
	}

	@Override
	protected OcpiLink authorizePeer( String authorization ) {
		return ocpiCredentialsModule.authorizePeer( authorization );
	}

	@Override
	protected OcpiResult<?> executeCredentials( OcpiRequestData oreq ) throws Exception {
		return ocpiCredentialsModule.commonInterface( oreq );
	}
}
