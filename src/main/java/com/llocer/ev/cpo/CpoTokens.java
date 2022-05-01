package com.llocer.ev.cpo;

import java.util.Iterator;

import com.llocer.common.SimpleMap;
import com.llocer.common.Tuple2;
import com.llocer.ev.ocpi.msgs22.OcpiToken;
import com.llocer.ev.ocpi.msgs22.OcpiToken.TokenType;
import com.llocer.ev.ocpi.msgs22.OcpiToken.Whitelist;
import com.llocer.ev.ocpp.msgs20.OcppAuthorizationData;
import com.llocer.ev.tarification.OcpiTypeTools;

public class CpoTokens implements SimpleMap<Tuple2<TokenType, String>, OcpiToken> {
	
	private final CPO cpo;
	private final SimpleMap<Tuple2<TokenType, String>, OcpiToken> tokens;

	CpoTokens( CPO cpo ) {
		this.cpo = cpo;
		
		this.tokens = CPO.mapFactory.make( 
					cpo.getId().toString()+".tokens", 
					OcpiToken.class, 
					Tuple2<TokenType,String>::keyString );
	}

	@Override
	public Iterator<OcpiToken> iterator() {
		return tokens.iterator();
	}

	@Override
	public OcpiToken get(Tuple2<TokenType, String> k) {
		return tokens.get(k);
	}

	@Override
	public void put(Tuple2<TokenType, String> k, OcpiToken ocpiToken ) {
		if( ocpiToken.getWhitelist() == Whitelist.ALLOWED ) {
			ocpiToken.setWhitelist( cpo.config.allowed_whitelist );
		}
		
		tokens.put( k, ocpiToken );
		
		if( k.f1 == TokenType.RFID ) {
			OcppAuthorizationData ocppAuthorizationData = OcpiTypeTools.toOcppAuthorizationData( ocpiToken );
			this.cpo.localToken( ocpiToken, ocppAuthorizationData );
		}
	}

	@Override
	public void remove(Tuple2<TokenType, String> k) {
		tokens.remove(k);
	}

	@Override
	public void clear() {
		tokens.clear();
	}

}
