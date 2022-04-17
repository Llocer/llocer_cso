package com.llocer.ev.cpo;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.llocer.ev.ocpi.msgs22.OcpiActiveChargingProfile;
import com.llocer.ev.ocpi.msgs22.OcpiAuthorizationInfo;
import com.llocer.ev.ocpi.msgs22.OcpiChargingProfile;
import com.llocer.ev.ocpi.msgs22.OcpiChargingProfile.ChargingRateUnit;
import com.llocer.ev.ocpi.msgs22.OcpiChargingProfilePeriod;
import com.llocer.ev.ocpi.msgs22.OcpiToken;
import com.llocer.ev.ocpi.msgs22.OcpiToken.TokenType;
import com.llocer.ev.ocpp.msgs20.AuthorizationStatusEnum;
import com.llocer.ev.ocpp.msgs20.ChargingProfileKindEnum;
import com.llocer.ev.ocpp.msgs20.ChargingProfilePurposeEnum;
import com.llocer.ev.ocpp.msgs20.ChargingRateUnitEnum;
import com.llocer.ev.ocpp.msgs20.IdTokenEnum;
import com.llocer.ev.ocpp.msgs20.OcppAuthorizationData;
import com.llocer.ev.ocpp.msgs20.OcppChargingProfile;
import com.llocer.ev.ocpp.msgs20.OcppChargingSchedule;
import com.llocer.ev.ocpp.msgs20.OcppChargingSchedulePeriod;
import com.llocer.ev.ocpp.msgs20.OcppCompositeSchedule;
import com.llocer.ev.ocpp.msgs20.OcppIdToken;
import com.llocer.ev.ocpp.msgs20.OcppIdTokenInfo;

public class OcpiTypeTools {
	public static TokenType fromIdTokenEnum( IdTokenEnum t ) {
		switch( t ) {
		case CENTRAL: return TokenType.APP_USER;
		case E_MAID: return TokenType.OTHER;
		case ISO_14443: return TokenType.RFID;
		case ISO_15693: return TokenType.RFID;
		case KEY_CODE: return TokenType.OTHER;
		case LOCAL: return TokenType.OTHER;
		case MAC_ADDRESS: return TokenType.OTHER;
		case NO_AUTHORIZATION: return TokenType.OTHER;
		}
		throw new IllegalArgumentException();
	}

	public static IdTokenEnum toIdTokenEnum( TokenType t ) {
		switch( t ) {
		case AD_HOC_USER:
			break;
		case APP_USER:
			return IdTokenEnum.CENTRAL;
		case OTHER:
			return IdTokenEnum.LOCAL;
		case RFID:
			return IdTokenEnum.ISO_14443;
		}
		throw new IllegalArgumentException();
	}

	public static OcppAuthorizationData toOcppAuthorizationData( OcpiToken ocpiToken ) {
		IdTokenEnum idTokenEnum = toIdTokenEnum( ocpiToken.getType() );
		
		OcppIdToken ocppIdToken = new OcppIdToken(); 
		ocppIdToken.setType( idTokenEnum );
		ocppIdToken.setIdToken( ocpiToken.getUid() );
		
		OcppIdTokenInfo idTokenInfo = new OcppIdTokenInfo();
		if( ocpiToken.getGroupId() != null ) {
			OcppIdToken groupIdToken = new OcppIdToken(); 
			groupIdToken.setType( idTokenEnum );
			groupIdToken.setIdToken( ocpiToken.getGroupId() );
			idTokenInfo.setGroupIdToken(groupIdToken);
			
		}
		idTokenInfo.setLanguage1( ocpiToken.getLanguage() );
		
		OcppAuthorizationData ocppAuthorizationData = new OcppAuthorizationData();
		ocppAuthorizationData.setIdToken(ocppIdToken);
		ocppAuthorizationData.setIdTokenInfo(idTokenInfo );
		
		return ocppAuthorizationData;
	}
	
	public static AuthorizationStatusEnum toAuthorizationStatusEnum( OcpiAuthorizationInfo.Allowed v) {
		if( v == null ) return null;
		
		switch( v ) {
		case ALLOWED: return AuthorizationStatusEnum.ACCEPTED;	
		case BLOCKED: return AuthorizationStatusEnum.BLOCKED;	
		case EXPIRED: return AuthorizationStatusEnum.EXPIRED;	
		case NOT_ALLOWED: return AuthorizationStatusEnum.INVALID;	
		case NO_CREDIT:	return AuthorizationStatusEnum.NO_CREDIT;	
		}
		
		throw new IllegalArgumentException(); 
	}
	
	public static OcppChargingProfile toOcppChargingProfile( OcpiChargingProfile ocpiChargingProfile ) {
		OcppChargingProfile ocppChargingProfile = new OcppChargingProfile();
		ocppChargingProfile.setId( 1 );
		ocppChargingProfile.setStackLevel( 0 );
		ocppChargingProfile.setChargingProfilePurpose( ChargingProfilePurposeEnum.TX_PROFILE );
//		ocppChargingProfile.transactionId => filled at caller

		/*
		 * ocppChargingSchedule
		 */
		
		OcppChargingSchedule ocppChargingSchedule = new OcppChargingSchedule();
		ocppChargingProfile.setChargingSchedule( Collections.singletonList( ocppChargingSchedule ) );

		ocppChargingSchedule.setId( 1 );
		
		if( ocpiChargingProfile.getStartDateTime() == null ) {
			// relative to session start
			ocppChargingProfile.setChargingProfileKind( ChargingProfileKindEnum.RELATIVE );
			
		} else {
			// absolute
			ocppChargingProfile.setChargingProfileKind( ChargingProfileKindEnum.ABSOLUTE );
			ocppChargingSchedule.setStartSchedule( ocpiChargingProfile.getStartDateTime() );
			
		}
		ocppChargingSchedule.setDuration( ocpiChargingProfile.getDuration() );
		
		switch( ocpiChargingProfile.getChargingRateUnit() ) {
		case A:
			ocppChargingSchedule.setChargingRateUnit( ChargingRateUnitEnum.A );
			break;
		case W:
			ocppChargingSchedule.setChargingRateUnit( ChargingRateUnitEnum.W );
			break;
		}
		
		ocppChargingSchedule.setMinChargingRate( ocpiChargingProfile.getMinChargingRate() );
		
		if( ocpiChargingProfile.getChargingProfilePeriod() == null ) {
			ocppChargingSchedule.setChargingSchedulePeriod( null );
		} else {
			ocppChargingSchedule.setChargingSchedulePeriod( new LinkedList<OcppChargingSchedulePeriod>() );
			for( OcpiChargingProfilePeriod ocpiChargingProfilePeriod : ocpiChargingProfile.getChargingProfilePeriod() ) {
				OcppChargingSchedulePeriod ocppChargingSchedulePeriod = new OcppChargingSchedulePeriod();
				ocppChargingSchedulePeriod.setStartPeriod( ocpiChargingProfilePeriod.getStartPeriod() );
				ocppChargingSchedulePeriod.setLimit( ocpiChargingProfilePeriod.getLimit() );
				ocppChargingSchedule.getChargingSchedulePeriod().add( ocppChargingSchedulePeriod );
			}
		}
		
		ocppChargingSchedule.setSalesTariff( null ); // optional
		
		return ocppChargingProfile;
	}

//	public static OcpiChargingProfile fromOcppChargingProfile( OcppChargingProfile ocppChargingProfile ) {
//		OcppChargingSchedule ocppChargingSchedule = ocppChargingProfile.getChargingSchedule().get(0);
//
//		OcpiChargingProfile ocpiChargingProfile = new OcpiChargingProfile();
//		ocpiChargingProfile.setStartDateTime(ocppChargingSchedule.getStartSchedule());
//		ocpiChargingProfile.setDuration(ocppChargingSchedule.getDuration());
//		switch( ocppChargingSchedule.getChargingRateUnit() ) {
//		case A:
//			ocpiChargingProfile.setChargingRateUnit( ChargingRateUnit.A );
//			break;
//		case W:
//			ocpiChargingProfile.setChargingRateUnit( ChargingRateUnit.W );
//			break;
//		}
//		ocpiChargingProfile.setMinChargingRate(ocppChargingSchedule.getMinChargingRate());
//		
//		if( ocppChargingSchedule.getChargingSchedulePeriod() == null ) {
//			ocpiChargingProfile.setChargingProfilePeriod(null);
//		} else {
//			List<OcpiChargingProfilePeriod> ocpiChargingProfilePeriods = new LinkedList<OcpiChargingProfilePeriod>();
//			for( OcppChargingSchedulePeriod chargingSchedulePeriod : ocppChargingSchedule.getChargingSchedulePeriod() ) {
//				OcpiChargingProfilePeriod ocpiChargingProfilePeriod = new OcpiChargingProfilePeriod();
//				ocpiChargingProfilePeriod.setStartPeriod(chargingSchedulePeriod.getStartPeriod());
//				ocpiChargingProfilePeriod.setLimit( chargingSchedulePeriod.getLimit() );
//				ocpiChargingProfilePeriods.add( ocpiChargingProfilePeriod );
//			}
//			ocpiChargingProfile.setChargingProfilePeriod(ocpiChargingProfilePeriods);
//		}
//		
//		return ocpiChargingProfile;
//	}

	public static OcpiActiveChargingProfile fromOcppCompositeSchedule( OcppCompositeSchedule ocppCompositeSchedule ) {

		OcpiChargingProfile ocpiChargingProfile = new OcpiChargingProfile();
		ocpiChargingProfile.setStartDateTime( ocppCompositeSchedule.getScheduleStart() );
		ocpiChargingProfile.setDuration( ocppCompositeSchedule.getDuration() );
		switch( ocppCompositeSchedule.getChargingRateUnit() ) {
		case A:
			ocpiChargingProfile.setChargingRateUnit( ChargingRateUnit.A );
			break;
		case W:
			ocpiChargingProfile.setChargingRateUnit( ChargingRateUnit.W );
			break;
		}
//		ocpiChargingProfile.setMinChargingRate();
		
		if( ocppCompositeSchedule.getChargingSchedulePeriod() == null ) {
			ocpiChargingProfile.setChargingProfilePeriod(null);
		} else {
			List<OcpiChargingProfilePeriod> ocpiChargingProfilePeriods = new LinkedList<OcpiChargingProfilePeriod>();
			for( OcppChargingSchedulePeriod chargingSchedulePeriod : ocppCompositeSchedule.getChargingSchedulePeriod() ) {
				OcpiChargingProfilePeriod ocpiChargingProfilePeriod = new OcpiChargingProfilePeriod();
				ocpiChargingProfilePeriod.setStartPeriod(chargingSchedulePeriod.getStartPeriod());
				ocpiChargingProfilePeriod.setLimit( chargingSchedulePeriod.getLimit() );
				ocpiChargingProfilePeriods.add( ocpiChargingProfilePeriod );
			}
			ocpiChargingProfile.setChargingProfilePeriod(ocpiChargingProfilePeriods);
		}
		
		OcpiActiveChargingProfile ocpiActiveChargingProfile = new OcpiActiveChargingProfile();
		ocpiActiveChargingProfile.setStartDateTime( ocppCompositeSchedule.getScheduleStart() ) ;
		ocpiActiveChargingProfile.setChargingProfile( ocpiChargingProfile );
		return ocpiActiveChargingProfile;
	}
}
