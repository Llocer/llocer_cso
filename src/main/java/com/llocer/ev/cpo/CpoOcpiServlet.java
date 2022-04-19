package com.llocer.ev.cpo;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.llocer.common.SimpleMap;
import com.llocer.ev.ocpi.modules.OcpiCredentialsModule;
import com.llocer.ev.ocpi.msgs22.OcpiEndpoint;
import com.llocer.ev.ocpi.msgs22.OcpiEndpoint.Identifier;
import com.llocer.ev.ocpi.msgs22.OcpiEndpoints;
import com.llocer.ev.ocpi.msgs22.OcpiVersions;
import com.llocer.ev.ocpi.server.OcpiAgentId;
import com.llocer.ev.ocpi.server.OcpiConfig;
import com.llocer.ev.ocpi.server.OcpiLink;
import com.llocer.ev.ocpi.server.OcpiRequestBuilder;
import com.llocer.ev.ocpi.server.OcpiRequestData;
import com.llocer.ev.ocpi.server.OcpiResult;
import com.llocer.ev.ocpi.server.OcpiResult.OcpiResultEnum;
import com.llocer.ev.ocpi.server.OcpiServlet;

public class CpoOcpiServlet extends OcpiServlet {

	private static final long serialVersionUID = -4764547316713059395L;
	
	// in order to skip URI.resolve problems, do not use initial slash and end by slash:
	private static final String servletPath = "cso/ocpi/"; 
	private static final String servletPath221 = servletPath+"221/";

	private static final OcpiEndpoints endpoints = initEndpoints();

	private static final SimpleMap< String /*own token*/, OcpiLink > linksByToken = 
			CPO.mapFactory.make( "credentials", OcpiLink.class);

	private static final OcpiCredentialsModule ocpiCredentialsModule = new OcpiCredentialsModule(linksByToken); 

	/*
	 * version and credentials
	 */

	@Override
	protected OcpiVersions[] getVersions() {
		OcpiVersions answer = new OcpiVersions();
		answer.setVersion( OcpiEndpoints.Version._2_2_1 );
		answer.setUrl( OcpiConfig.getPublicURI().resolve( servletPath221 ) );
		return new OcpiVersions[] { answer };
	}

	private static OcpiEndpoints initEndpoints() {
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
		endpoint.setRole( OcpiEndpoint.InterfaceRole.SENDER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.SESSIONS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.SENDER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.CDRS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.SENDER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.TARIFFS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.SENDER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.TOKENS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.RECEIVER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.COMMANDS );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.RECEIVER );
		answer.getEndpoints().add( endpoint );

		endpoint = new OcpiEndpoint();
		endpoint.setIdentifier( OcpiEndpoint.Identifier.CHARGINGPROFILES );
		endpoint.setRole( OcpiEndpoint.InterfaceRole.RECEIVER );
		answer.getEndpoints().add( endpoint );

		for( OcpiEndpoint e : answer.getEndpoints() ) {
			e.setUrl( OcpiConfig.getPublicURI()
					.resolve( servletPath221+e.getIdentifier().toString()+"/" ));
		}
		
		return answer;
	}
	
	@Override
	protected OcpiEndpoints getEndpoints( String version ) {
		return endpoints;
	}

	static URI getEndpoint( Identifier module ) {
		return OcpiRequestBuilder.getModuleUrl(endpoints, module);
	}
	
	/*
	 * agents & execution
	 */
	
	private static final Map<OcpiAgentId, OcpiAgent> agentById = new ConcurrentHashMap<OcpiAgentId, OcpiAgent>();
	private static OcpiAgent defaultAgent = null;

	public static void registerAgent( OcpiAgent agent ) {
		agentById.put( agent.getId(), agent );
	}
	
	public static void setDefaultAgent( OcpiAgent agent ) {
		defaultAgent = agent;
	}

	@Override
	protected OcpiResult<?> execute( OcpiRequestData oreq ) throws Exception {
		OcpiAgent agent = ( oreq.to == null ? defaultAgent : agentById.get( oreq.to ) );
		if( agent == null ) return OcpiResultEnum.UNKNOWN_TO_ADDRESS;
		
		return agent.executeRequest( oreq );
	}

	/*
	 * links & credentials
	 */
	
	@Override
	protected OcpiLink authorizePeer(String authorization) {
		return ocpiCredentialsModule.authorizePeer(authorization);
	}
	
	@Override
	protected OcpiResult<?> executeCredentials( OcpiRequestData oreq ) throws Exception {
		return ocpiCredentialsModule.commonInterface(oreq);
	}

	static OcpiLink getLink( OcpiAgentId agentId ) {
		return ocpiCredentialsModule.getLinkByAgentId( agentId );
	}

	static Collection<OcpiLink> getLinks() {
		return ocpiCredentialsModule.getLinks();
	}
	
	public static void allowLink(OcpiLink link) {
		ocpiCredentialsModule.allowLink( link );
	}
}
