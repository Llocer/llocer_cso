#/bin/bash

CSO="http://127.0.0.1:8080/llocer_cso_war/cso/ocpi"
HEADERS=(-H 'Authorization: Token AEMSP' -H 'X-Request-ID: oamRequestId' -H 'X-Correlation-ID: oamCorrelationId' -H 'OCPI-to-country-code: ES' -H 'OCPI-to-party-id: MCS')

curl -v "${HEADERS[@]}" "$CSO/221/locations?limit=1" 

