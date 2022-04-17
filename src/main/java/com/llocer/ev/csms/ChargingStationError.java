package com.llocer.ev.csms;

import com.llocer.ev.ocpp.server.OcppError;
import com.llocer.ev.ocpp.server.OcppMsg.OcppErrorCode;

enum ChargingStationError implements OcppError{
	WaitingBoot( OcppErrorCode.GenericError, "waiting boot message" ),
	NotImplemented( OcppErrorCode.NotImplemented, "not known " ),
	NotSupported( OcppErrorCode.NotSupported, "not implemented " );
	
	private final OcppErrorCode errorCode;
	private final String errorDescription;
	
	ChargingStationError( OcppErrorCode errorCode, String errorDescription ) {
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

