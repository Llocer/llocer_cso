#!/bin/bash

#TEST=heartbeat.scala
#TEST=boot.scala
TEST=do-a-transaction.scala
#TEST=remote-transaction.scala

CHARGE_POINT="RDAM 123"
SERVER_URL="ws://127.0.0.1:8080/llocer_cso_war/cso/ocpp"

cd $HOME/varios/evse/docile-charge-point
java -jar cmd/target/scala-2.12/docile.jar -c "$CHARGE_POINT" -v 2.0 $SERVER_URL examples/ocpp20/$TEST
