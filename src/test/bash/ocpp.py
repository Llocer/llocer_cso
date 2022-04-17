#!/usr/bin/python3

server_url="ws://127.0.0.1:8080/llocer_cso_war/cso/ocpp/RDAM%20123"

# profile with: visualvm

import websocket # pip3 install websocket-client
import json
from time import sleep, time
import ssl
import uuid
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('testId', help='Test name')
args = parser.parse_args()

transactionId = str(uuid.uuid4())

def log( msg ):
    print("\n%d %s"%(time()%100000,msg))

def no_mask( s ): 
    return b'\0\0\0\0'

ws = websocket.WebSocket( sslopt={"cert_reqs": ssl.CERT_NONE}, get_mask_key=no_mask )
ws.connect( server_url )

def send( msg ):
    global ws
    js = json.dumps( msg )
    log( "<<< %s ..."%js )
    ws.send( js )

def recv():
    global msg
    log( "waiting msg ..." )
    js = ws.recv()

    if not js:
        log( "empty message, ending." )
        exit(0)

    log( ">>> %s"%js )
    msg = json.loads( js )
    return msg

def testBoot():
    send( [ 2, str(uuid.uuid4()), "BootNotification", 
    { "reason": "PowerUp",
      "chargingStation": {
    	"model": "pulsar",
    	"vendorName": "wallbox"
      } 
    } ] )
    
    recv() # boot
    
    #recv() # get variables
    #send( [ 3, msg[1], {} ] )
    #sleep( 0.1 )
    
    send( [ 2, str(uuid.uuid4()), "BootNotification", 
    { 
        "reason": "PowerUp",
        "chargingStation": {
    	"model": "pulsar",
    	"vendorName": "wallbox"
        } 
    } ] )
    recv() # boot
    
    tr = {
        "eventType": "Started",
        "timestamp": "2021-12-27T09:30:00Z",
        "triggerReason": "CablePluggedIn",
        "seqNo": 14001,
        "transactionInfo": {
          "transactionId": transactionId,
          "chargingState": "Charging"
        },
        "idToken": {
    	"idToken": "12345678905880",
    	"type": "ISO14443"
        },
        "evse": {
          "id": 1
        }
    }
    tr["meterValue"] = [ {
    	"timestamp": tr["timestamp"],
    	"sampledValue": [ {
    		"value": 1000,
    		"measurand": "Energy.Active.Import.Register",
    		"unitOfMeasure": {
    			"unit": "kWh"
    		}
    	}, {
    		"value": 12,
    		"measurand": "Current.Import",
    		"unitOfMeasure": {
    			"unit": "A"
    		}
    	} ]
    } ]
    send( [ 2, str(uuid.uuid4()), "TransactionEvent", tr  ] )
    recv() 
    
    tr["eventType"] = "Updated"
    tr["seqNo"] = 14002
    tr["timestamp"] = "2021-12-27T12:15:00Z"
    tr["transactionInfo"] = {
          "transactionId": transactionId,
          "chargingState": "Idle"
        }
    tr["meterValue"] = [ {
    	"timestamp": tr["timestamp"],
    	"sampledValue": [ {
    		"value": 1020,
    		"measurand": "Energy.Active.Import.Register",
    		"unitOfMeasure": {
    			"unit": "kWh"
    		}
    	}, {
    		"value": 0,
    		"measurand": "Current.Import",
    		"unitOfMeasure": {
    			"unit": "A"
    		}
    	} ]
    } ]
    send( [ 2, str(uuid.uuid4()), "TransactionEvent", tr  ] )
    recv()
    
    #request=recv() # setChargingProfile
    #answer={}
    #answer["status"]="Accepted"
    #send( [ 3, request[1],  answer  ] )
    
    tr["eventType"] = "Ended"
    tr["seqNo"] = 14003
    tr["timestamp"] = "2021-12-27T12:57:00Z"
    tr["transactionInfo"] = {
          "transactionId": transactionId
    }
    del tr["meterValue"]
    send( [ 2, str(uuid.uuid4()), "TransactionEvent", tr  ] )
    recv()
    
     
if args.testId == 'boot':
    testBoot()    
    
    
