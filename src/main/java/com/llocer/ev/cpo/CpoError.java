package com.llocer.ev.cpo;

import com.llocer.ev.ocpp.server.OcppError;
import com.llocer.ev.ocpp.server.OcppMsg.OcppErrorCode;

enum CpoError implements OcppError{
	eMSPUnreachable( OcppErrorCode.GenericError, "The eMSP is unreachable" );
	
	private final OcppErrorCode errorCode;
	private final String errorDescription;
	
	CpoError( OcppErrorCode errorCode, String errorDescription ) {
		this.errorCode = errorCode;
		this.errorDescription = errorDescription;
	}
	
	@Override
	public OcppErrorCode getErrorCode() {
		return errorCode;
	}
	
	@Override
	public String getErrorDescription() {
		return errorDescription;
	}
	
}

