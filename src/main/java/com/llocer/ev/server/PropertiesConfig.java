package com.llocer.ev.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.llocer.common.Log;

public class PropertiesConfig {
	private static String configFileName = "/etc/llocer/ev.properties";
	
	public static final Properties properties = initProperties();

	public static String redisServerAddress = "redis://localhost:6379";
	public static String etcDir = "/opt/llocer/ev/etc";
	public static String varDir = "/opt/llocer/ev/var";
	
	private static Properties initProperties() {
		Log.debug( "PropertiesConfig.initProperties" );
		
		try (InputStream input = new FileInputStream( configFileName )) {
            properties.load(input);
            
		} catch (IOException e) {
			Log.warning( "unable to read config file "+configFileName );
			return properties;
			
		}
		
		for( String key : properties.stringPropertyNames() ) {
			switch( key ) {
			case "etcDir":
				etcDir = properties.getProperty(key);
				break;
				
			case "varDir":
				varDir = properties.getProperty(key);
				break;
				
			case "redisServerAddress":
				redisServerAddress = properties.getProperty(key);
				break;
			}
		}
		
		return properties;
	}
}
