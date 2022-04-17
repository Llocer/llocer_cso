package com.llocer.ev.server;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llocer.common.Log;

public class ServletOAM extends HttpServlet {
	private static final long serialVersionUID = 4675826794199145277L;
	
	public static final ObjectMapper mapper = new ObjectMapper();

	private static Map<String,OAMAgent> agents = new HashMap<String,OAMAgent>(); 

	public static void registerAgent(String agentId, OAMAgent agent) {
		agents.put( agentId, agent );
	}
	
	private void executeRequest( HttpServletRequest request, HttpServletResponse response ) { 
		try {
			String uri_s = request.getRequestURI();
			Log.debug( "ServletOAM.executeRequest: %s %s", request.getMethod(), uri_s );
			
			String uri[] = uri_s.split("/"); 
			
			int uriIdx = ( uri.length > 0 && uri[0].isEmpty() ? 2 : 1 );
			if( uri.length <= uriIdx ) {
				response.sendError( 400 );
				return;
			};
			
			String agentId = uri[uriIdx];
			OAMAgent agent = agents.get( agentId );
			if( agent == null ) {
				response.sendError( 404 );
				return;
				
			}
			
			for( int i = uriIdx; i< uri.length; i++ ) {
				uri[i-uriIdx] = URLDecoder.decode( uri[i], StandardCharsets.UTF_8 );
			}

			agent.executeOAM( uri, request, response );
			
		} catch( Exception exc ) {
			Log.error(exc);

			try {
				response.sendError( 500 );

			} catch( IOException exc2 ) {
				Log.error(exc2);

			}
		}
	}
	
	public static void fillResponse( HttpServletResponse response, Object answer ) throws Exception { 

			if( answer == null ) {
				response.setStatus( 200 );
			
			} else if( answer instanceof Integer ) {
				response.setStatus( (Integer)answer );
			
			} else if( answer instanceof String ) {
				response.setStatus( 200 );
				response.setContentType( "text/html" );
				response.getWriter().append( (String)answer );
				
			} else {
				String responseBody = mapper.writeValueAsString(answer);
				Log.debug( "ServletOAM.handleRequest: response.body=%s", responseBody );
				response.setStatus( 200 );
				response.setContentType( "application/json" );
				response.getWriter().append( responseBody );
				
			}			
	}
	
	@Override
    protected void doPost(
    		HttpServletRequest request, 
    		HttpServletResponse response) throws ServletException, IOException {
		executeRequest( request, response );	
    }

	@Override
	protected void doGet( 
			HttpServletRequest request, 
			HttpServletResponse response) throws ServletException, IOException {
		executeRequest( request, response );	
	}
	
//	private Object executeRequest( CpoOAMCommand command ) { 
//		switch( command.type ) {
//		case "newOcpiLink": {
//			OcpiMakeLinkCommand tcommand = (OcpiMakeLinkCommand)command;
//			CPO cpo = CPO.getCPO( tcommand.cpo );
//			cpo.makeServiceLink( tcommand.ownToken, tcommand.peerRole );
//		}
//		
//		case "sendOcppUpdateFirmware": {
//			SendOcppUpdateFirmwareCommand tcommand = (SendOcppUpdateFirmwareCommand)command;
//			ChargingStation cs = ChargingStation.getChargingStation( tcommand.cs );
//			cs.send( tcommand.message, null );
//		}
//		
//		default:
//			return 404;
//		}
//	}
	
}
