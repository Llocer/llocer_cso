package com.llocer.ev.server;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.llocer.collections.SimpleMapFactory;
import com.llocer.common.Log;
import com.llocer.common.SimpleMap;
import com.llocer.ev.cpo.CPO;
import com.llocer.ev.csms.ChargingStation;
import com.llocer.ev.ocpi.server.OcpiAgentId;
import com.llocer.redis.RedisClientConnection;

class State {
	public Instant lastAlive = Instant.now();
}

class ChargingStationConfig {
	public String id;
	public String csmsId;
}

class Config {
	public List< OcpiAgentId > cpos;
	public List< ChargingStationConfig > chargingStations;
}

@WebListener
public class MainStartStop implements ServletContextListener {

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final ObjectReader reader = mapper.readerFor( Config.class );


	private static final String id = "unique";
	private static final SimpleMapFactory mapFactory = SimpleMapFactory.forName( "com.llocer.redis.RedisMapFactory" );
	private static final SimpleMap<String,State> stateMap = mapFactory.make( "CS::", State.class );
	private static State state;

	static public ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

	private static class Controller implements Runnable {

		public Controller() {
		}

		@Override
		public void run() {
			state.lastAlive = Instant.now();
			stateMap.put(id, state);
		}
	}

	private ScheduledFuture<?> controller = null;

	private void init( ) throws Exception {
		Config config = reader.readValue( new File( PropertiesConfig.etcDir+"/cpo.conf" ) );

		RedisClientConnection.connect( PropertiesConfig.redisServerAddress );
		
		state = stateMap.get(id);
		if( state == null ) state = new State();

		if( config.cpos != null ) {
			for( OcpiAgentId cpo : config.cpos ) {
				JsonNode jsonNode = mapper.readTree( new File( PropertiesConfig.etcDir+"/"+"cpo::"+cpo.countryCode+"::"+cpo.partyId ) );
				CPO.make( jsonNode );
			}
		}

		if( config.chargingStations != null ) {
			for( ChargingStationConfig cs : config.chargingStations ) {
				ChargingStation.make( cs.id );
			}
		}
		
		this.controller  = MainStartStop.scheduler.scheduleAtFixedRate( new Controller(), 120, 60, TimeUnit.SECONDS );
	}
	
	@Override
	public void contextInitialized( ServletContextEvent sce ) {
		try {
			init();
		} catch (Exception e) {
			Log.error( e, "error at initilization");
		}
	}

	@Override
	public void contextDestroyed( ServletContextEvent sce ) {
		if( this.controller != null ) {
			this.controller.cancel(true);
			this.controller = null;
		}

		scheduler.shutdown();
		RedisClientConnection.shutdown();
	}
	
	public static Instant getLastAlive() {
		return state.lastAlive;
	}
}
