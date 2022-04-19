package com.llocer.ev.cpo;

import com.llocer.ev.ocpp.server.OcppErrorItf;
import com.llocer.ev.ocpp.server.OcppMsg.OcppErrorCode;

enum CpoError implements OcppErrorItf{
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

