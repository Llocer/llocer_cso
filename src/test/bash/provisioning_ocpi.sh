#/bin/bash

#set -x

CSO="http://127.0.0.1:8080/llocer_cso_war/cso/oam"
HEADERS=(-H 'Authorization: Token AOAM' -H 'X-Request-ID: oamRequestId' -H 'X-Correlation-ID: oamCorrelationId' -H 'OCPI-to-country-code: ES' -H 'OCPI-to-party-id: MCS') 

# location
declare -a locations=("location1" "location2" "location3" "location4")
hour=10

for location in "${locations[@]}"; do
curl -X PUT "${HEADERS[@]}" "$CSO/221/locations/ES/MCS/$location/" -d @- <<END
{
	"country_code": "ES",
	"party_id": "MCS",
	"id": "$location",
	"publish": true,
	"address": "Sesame street",
	"country": "ESP",
	"coordinates": { "latitude": "my latitude", "longitude": "my longitude" },
	"time_zone": "Europe/Madrid"
}
END
echo
	#"last_updated": "2019-06-24T$hour:39:09Z"
let hour++
done
echo

curl -X PATCH "${HEADERS[@]}" "$CSO/221/locations/ES/MCS/location1/" -d @- <<END
{
	"address": "Barrio Sesamo",
	"coordinates": { "latitude": "my latitude 2" }
}
END
echo

echo PUT evse ...
curl -X PUT "${HEADERS[@]}" "$CSO/221/locations/ES/MCS/location1/BE*BEC*E041503003/" -d @- <<END
{
	"uid": "3256",
	"evse_id": "BE*BEC*E041503003",
	"status": "AVAILABLE",
		"capabilities": ["RESERVABLE"],
		"connectors": [ {
			"id": "BE*BEC*E041503003*1",
			"standard": "IEC_62196_T2",
			"format": "SOCKET",
			"tariff_ids": ["14"]
		} ],
	"floor_level": -1,
	"physical_reference": 3,
	"last_updated": "2019-06-24T12:39:09Z"
}
END
echo

echo First hour free energy example
curl -X PUT "${HEADERS[@]}" "$CSO/221/tariffs/ES/MCS/52/" -d @- <<END
{
	"country_code": "ES",
	"party_id": "MCS",
	"id": "52",
	"currency": "EUR",
	"elements": [{
		"price_components": [{
			"type": "PARKING_TIME",
			"price": 0.0,
			"step_size": 60
		}],
		"restrictions": {
			"max_duration": 3600
		}
	}, {
		"price_components": [{
			"type": "PARKING_TIME",
			"price": 2.0,
			"step_size": 60
		}],
		"restrictions": {
			"max_duration": 10800
		}
	}, {
		"price_components": [{
			"type": "PARKING_TIME",
			"price": 3.0,
			"step_size": 60
		}]
	}, {
		"price_components": [{
			"type": "ENERGY",
			"price": 0.0,
			"step_size": 1
		}],
		"restrictions": {
			"max_kwh": 1.0
		}
	}, {
		"price_components": [{
			"type": "ENERGY",
			"price": 0.2,
			"step_size": 1
		}]
	}],
	"last_updated": "2018-12-29T15:55:58Z"
}
END
echo

echo complex tariff example
curl -X PUT "${HEADERS[@]}" "$CSO/221/tariffs/ES/MCS/14/" -d @- <<END
{
	"country_code": "DE",
	"party_id": "ALL",
	"id": "14",
	"currency": "EUR",
	"type": "REGULAR",
	"tariff_alt_url": "https://company.com/tariffs/14",
	"elements": [{
		"price_components": [{
			"type": "FLAT",
			"price": 2.50,
			"vat": 15.0,
			"step_size": 1
		}]
	}, {
		"price_components": [{
			"type": "TIME",
			"price": 1.00,
			"vat": 20.0,
			"step_size": 900
		}],
		"restrictions": {
			"max_current": 32.00
		}
	}, {
		"price_components": [{
			"type": "TIME",
			"price": 2.00,
			"vat": 20.0,
			"step_size": 600
		}],
		"restrictions": {
			"min_current": 32.00,
			"day_of_week": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
		}
	}, {
		"price_components": [{
			"type": "TIME",
			"price": 1.25,
			"vat": 20.0,
			"step_size": 600
		}],
		"restrictions": {
			"min_current": 32.00,
			"day_of_week": ["SATURDAY", "SUNDAY"]
		}
	}, {
		"price_components": [{
			"type": "PARKING_TIME",
			"price": 5.00,
			"vat": 10.0,
			"step_size": 300
		}],
		"restrictions": {
			"start_time": "09:00",
			"end_time": "18:00",
			"day_of_week": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
		}
	}, {
		"price_components": [{
			"type": "PARKING_TIME",
			"price": 6.00,
			"vat": 10.0,
			"step_size": 300
		}],
		"restrictions": {
			"start_time": "10:00",
			"end_time": "17:00",
			"day_of_week": ["SATURDAY"]
		}
	}],
	"last_updated": "2015-06-29T20:39:09Z"
}
END
echo



echo token
curl -X PUT "${HEADERS[@]}" "$CSO/221/tokens/US/EMS/01020304" -d @- <<END
{
	"country_code": "US",
	"party_id": "EMS",
	"uid": "12345678905880",
	"type": "RFID",
	"contract_id": "DE8ACC12E46L89",
	"visual_number": "DF000-2001-8999-1",
	"issuer": "TheNewMotion",
	"group_id": "DF000-2001-8999",
	"valid": true,
	"whitelist": "ALLOWED",
	"language": "it",
	"default_profile_type": "GREEN",
	"energy_contract": {
		"supplier_name": "Greenpeace Energy eG",
		"contract_id": "0123456789"
	},
	"last_updated": "2018-12-10T17:25:10Z"
}
END
echo
