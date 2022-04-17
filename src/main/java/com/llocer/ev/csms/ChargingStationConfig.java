package com.llocer.ev.csms;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.llocer.common.Log;
import com.llocer.ev.ocpp.server.OcppAction;
import com.llocer.ev.server.PropertiesConfig;

class BootCommand {
	OcppAction action = null; 
	Object payload = null;
}

class BootCommandDeserializer extends JsonDeserializer<BootCommand> {
	@Override
	public BootCommand deserialize( JsonParser jp, DeserializationContext ctxt )
			throws IOException, JsonProcessingException {
		
		ObjectMapper mapper = (ObjectMapper) jp.getCodec();
		JsonNode node = mapper.readTree(jp);
		
        BootCommand res = new BootCommand();
        
		String action_s = node.get(0).asText();
		try {
			res.action = OcppAction.valueOf( action_s );
			res.payload = res.action.decodeRequest( node.get(1) );
			
		} catch( Exception e ) {
			throw new IOException();
			
		}
		
		
        return res;
	}
}

public class ChargingStationConfig {
	private static final ObjectMapper mapper = initMapper();
	private static final ObjectReader reader = mapper.readerFor(ChargingStationConfig.class);

	public String csms;
	public List<BootCommand> boot_commands = null;
	public Integer bootInterval = 2; // seconds
	
	private static ObjectMapper initMapper() {
		ObjectMapper mapper = new ObjectMapper();

		SimpleModule module = new SimpleModule();
		module.addDeserializer( BootCommand.class, new BootCommandDeserializer() ); 
		mapper.registerModule( module );

		return mapper;
	}

	
	public static ChargingStationConfig read( String id ) {
		String filename = PropertiesConfig.etcDir+"/cs::"+id;
		ChargingStationConfig config;
		try {
			config = reader.readValue( new File( filename ) );
		} catch (Exception e2) {
			Log.error( e2, "unable to read config file "+ filename );
			throw new IllegalArgumentException();
		}
		
//		config.organize();
		return config;
	}

}
