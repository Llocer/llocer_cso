#/bin/bash

SERVER=http://127.0.0.1:8080/llocer_cso_war
CSO=$SERVER/cso/ocpi
	
curl -X PUT -H "Authorization: Token AEMSP" $CSO/221/chargingprofiles/testTransactionId -d @- <<END
{
	"charging_profile": {
		"start_date_time": "2020-01-09T17:00:00Z",
		"duration": 10800,
		"charging_rate_unit": "W",
		"min_charging_rate": 100,
		"charging_profile_period": [ {
			"start_period": 0,
			"limit": 2000
		} ]
	},
	"response_url": "$SERVER/emsp/ocpi/response"
}
END

