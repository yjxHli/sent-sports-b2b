#!/usr/bin/env bash
set -e
BASE=http://localhost:8088/api

jtok() { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{let j=JSON.parse(s);let d=j.data||{};process.stdout.write(String(d.token||d.accessToken||d.jwt||""))}catch(e){process.stdout.write("")}})'; }
jget() { P="$1" node -e 'let s="";let p=process.env.P;process.stdin.on("data",d=>s+=d).on("end",()=>{try{let j=JSON.parse(s);let parts=p.split(".");let v=j;for(const k of parts){if(v==null)break;v=v[k]}process.stdout.write(v==null?"":String(v))}catch(e){process.stdout.write("ERR")}})'; }
jpretty() { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{let j=JSON.parse(s);process.stdout.write(JSON.stringify(j.data!==undefined?j.data:j,null,2))}catch(e){process.stdout.write(s)}})'; }

echo "== 1. login admin =="
LOGIN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456","aud":"admin","deviceId":"disc-verify"}')
TOKEN=$(echo "$LOGIN" | jtok)
echo "token len: ${#TOKEN}"
if [ -z "$TOKEN" ]; then echo "LOGIN RAW: $LOGIN"; exit 1; fi
AUTH="Authorization: Bearer $TOKEN"

echo "== 2. admin product list (first product) =="
LIST=$(curl -s "$BASE/admin/products?page=1&size=1" -H "$AUTH")
echo "$LIST" | jpretty
PROD=$(echo "$LIST" | jget data.list.0.productId)
DSC_BEFORE=$(echo "$LIST" | jget data.list.0.discountSkuCount)
SKC_BEFORE=$(echo "$LIST" | jget data.list.0.skuCount)
echo "PROD=$PROD discountSkuCount(before)=$DSC_BEFORE skuCount=$SKC_BEFORE"

echo "== 3. catalog detail =="
DET=$(curl -s "$BASE/products/$PROD" -H "$AUTH")
echo "$DET" | jpretty | head -40
SKU=$(echo "$DET" | jget data.skus.0.skuId)
RETAIL=$(echo "$DET" | jget data.skus.0.price.retailPriceCent)
echo "SKU=$SKU retailPriceCent=$RETAIL"

echo "== 4. config ladder (rate 80 = 八折) =="
curl -s -X PUT "$BASE/admin/skus/$SKU/ladder-prices" -H "$AUTH" -H 'Content-Type: application/json' -d "{\"skuId\":$SKU,\"tiers\":[{\"minQty\":1,\"maxQty\":-1,\"rate\":80}]}" | jpretty
echo

echo "== 5. read ladder-prices (expect 1 tier rate 80) =="
curl -s "$BASE/admin/skus/$SKU/ladder-prices" -H "$AUTH" | jpretty
echo

echo "== 6. catalog detail again (expect ladderPriceCent = floor(retail*80/100)) =="
DET2=$(curl -s "$BASE/products/$PROD" -H "$AUTH")
LPC=$(echo "$DET2" | jget data.skus.0.price.ladderPriceCent)
echo "ladderPriceCent=$LPC  retail=$RETAIL  expected=$(( RETAIL * 80 / 100 ))"

echo "== 7. admin product list (expect discountSkuCount +1) =="
DSC_AFTER=$(curl -s "$BASE/admin/products?page=1&size=1" -H "$AUTH" | jget data.list.0.discountSkuCount)
echo "discountSkuCount(before)=$DSC_BEFORE (after)=$DSC_AFTER"

echo "== 8. clear ladder (tiers []) =="
curl -s -X PUT "$BASE/admin/skus/$SKU/ladder-prices" -H "$AUTH" -H 'Content-Type: application/json' -d "{\"skuId\":$SKU,\"tiers\":[]}" | jpretty
echo

echo "== 9. verify rollback =="
echo -n "ladder-prices: "; curl -s "$BASE/admin/skus/$SKU/ladder-prices" -H "$AUTH" | jpretty
echo -n "ladderPriceCent: "; curl -s "$BASE/products/$PROD" -H "$AUTH" | jget data.skus.0.price.ladderPriceCent; echo
echo -n "discountSkuCount: "; curl -s "$BASE/admin/products?page=1&size=1" -H "$AUTH" | jget data.list.0.discountSkuCount; echo

echo "== 10. customer price upsert =="
CP=$(curl -s -X POST "$BASE/admin/customer-prices" -H "$AUTH" -H 'Content-Type: application/json' -d "{\"skuId\":$SKU,\"customerId\":9002,\"priceCent\":$((RETAIL-1000)),\"validFromMillis\":0,\"validToMillis\":0}")
echo "$CP" | jpretty
CPID=$(echo "$CP" | jget data.id)
echo "customerPrice id=$CPID"

echo "== 11. list customer-prices (expect 1) =="
curl -s "$BASE/admin/customer-prices?skuId=$SKU&customerId=9002" -H "$AUTH" | jpretty
echo

echo "== 12. delete customer price =="
curl -s -X DELETE "$BASE/admin/customer-prices/$CPID" -H "$AUTH" | jpretty
echo

echo "== 13. verify empty =="
curl -s "$BASE/admin/customer-prices?skuId=$SKU&customerId=9002" -H "$AUTH" | jpretty
echo

echo "DONE"
