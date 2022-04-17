package com.llocer.ev.csms;

import com.llocer.ev.ocpp.msgs20.OcppIdToken;
import com.llocer.ev.ocpp.msgs20.OcppIdTokenInfo;
import com.llocer.ev.ocpp.msgs20.OcppStatusNotificationRequest;

public interface CSMS {
	Transaction getTransactionById( String transactionId, ChargingStation cs, Integer reservationId );

	void handleStatusNotification( ChargingStation cs, OcppStatusNotificationRequest request );
	
	OcppIdTokenInfo authorize( ChargingStation cs, OcppIdToken idToken ) throws Exception;

	void reservationFinished( ChargingStation cs, Integer ocppReservationId, boolean removed );

	boolean signChargingStationCSR( String csr );
}
