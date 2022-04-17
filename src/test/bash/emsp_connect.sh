#/bin/bash

CSO="http://127.0.0.1:8080/llocer_cso_war/cso/ocpi"
HEADERS=(-H 'Authorization: Token AEMSP' -H 'X-Request-ID: testRequestId' -H 'X-Correlation-ID: testCorrelationId')

curl "${HEADERS[@]}" $CSO
echo
echo ---------------

curl "${HEADERS[@]}" $CSO/221
echo
echo ---------------

curl "${HEADERS[@]}" $CSO/221/credentials -d @- <<END
{
	"token": "ATEST",
	"url": "http://127.0.0.1:8080/llocer_cso_war/emsp/ocpi",
	"roles": [{
		"role": "EMSP",
		"country_code": "US",
		"party_id": "EMS",
		"business_details": {
			"name": "Example Operator",
			"logo": {
				"url": "https://example.com/img/logo.jpg",
				"thumbnail": "https://example.com/img/logo_thumb.jpg", 
				"category": "OPERATOR",
				"type": "jpeg",
				"width": 512,
				"height": 512
			},
			"website": "http://example.com"
		}
	}]
}
END

