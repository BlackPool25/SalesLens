import requests
import time
import os
import json

BASE_URL = "http://localhost:8500"

def main():
    print("=== STARTING E2E INTEGRATION TEST ===")
    
    # 1. Register User
    username = f"user_{int(time.time())}"
    password = "SecurePassword123!"
    email = f"{username}@example.com"
    
    reg_payload = {
        "username": username,
        "password": password,
        "email": email,
        "firstName": "John",
        "lastName": "Doe"
    }
    
    print(f"Registering user: {username}")
    reg_resp = requests.post(f"{BASE_URL}/auth/register", json=reg_payload)
    if reg_resp.status_code not in (200, 201):
        print("Registration failed:", reg_resp.text)
        return
    print("User registered successfully.")
    
    # 2. Login User
    login_payload = {
        "identifier": username,
        "password": password
    }
    print("Logging in...")
    login_resp = requests.post(f"{BASE_URL}/auth/login", json=login_payload)
    if login_resp.status_code != 200:
        print("Login failed:", login_resp.text)
        return
    
    auth_data = login_resp.json()
    token = auth_data["accessToken"]
    headers = {"Authorization": f"Bearer {token}"}
    print("Logged in successfully. JWT Token acquired.")
    
    # 3. Create Data Source
    create_src_payload = {
        "name": f"Superstore Source {int(time.time())}",
        "sourceType": "CSV_FILE",
        "trustScore": 0.9,
        "active": True,
        "connectionConfig": '{"filePath": "/tmp/non_existent.csv"}'
    }
    print("Creating data source...")
    src_resp = requests.post(f"{BASE_URL}/datasources/create-source", json=create_src_payload, headers=headers)
    if src_resp.status_code not in (200, 201):
        print("Create data source failed:", src_resp.text)
        return
    
    # Retrieve all sources to find the UUID of the one we just created
    list_resp = requests.get(f"{BASE_URL}/datasources/get-all-sources", headers=headers)
    if list_resp.status_code != 200:
        print("List data sources failed:", list_resp.text)
        return
        
    sources = list_resp.json()
    source_name = create_src_payload["name"]
    source_id = None
    for src in sources:
        if src["name"] == source_name:
            source_id = src["id"]
            break
            
    if not source_id:
        print(f"Failed to find data source with name: {source_name}")
        return
        
    print(f"Data Source created and resolved to ID: {source_id}")
    
    # 4. Generate CSV v1 with 22 rows
    csv_v1_path = "superstore_v1.csv"
    with open(csv_v1_path, "w") as f:
        f.write("Row ID,Customer Name,Ship Mode,Order Date,Sales\n")
        for i in range(1, 23):
            ship_mode = "Standard Class" if i % 3 == 0 else "First Class" if i % 3 == 1 else "Second Class"
            f.write(f"{i},Customer {i},{ship_mode},2024-03-15,123.45\n")
            
    print(f"Generated {csv_v1_path} with 22 rows.")
    
    # 5. Ingest CSV v1
    print("Uploading CSV v1 for ingestion...")
    with open(csv_v1_path, "rb") as f:
        files = {"file": (os.path.basename(csv_v1_path), f, "text/csv")}
        params = {"sourceId": source_id}
        ingest_resp = requests.post(f"{BASE_URL}/api/v1/ingest/csv", files=files, data=params, headers=headers)
        
    if ingest_resp.status_code not in (200, 202, 201):
        print("Ingestion upload failed:", ingest_resp.status_code, ingest_resp.text)
        return
        
    ingest_data = ingest_resp.json()
    job_id = ingest_data["jobId"]
    print(f"Ingestion job started with ID: {job_id}")
    
    # 6. Poll Ingestion Job Status
    job_completed = False
    for attempt in range(15):
        print(f"Polling job status (attempt {attempt+1})...")
        job_resp = requests.get(f"{BASE_URL}/api/v1/jobs/{job_id}", headers=headers)
        if job_resp.status_code == 200:
            job_status = job_resp.json().get("status")
            print(f"Job Status: {job_status}")
            if job_status == "COMPLETED":
                job_completed = True
                break
            elif job_status in ("FAILED", "ABORTED"):
                print("Job failed or was aborted:", job_resp.text)
                return
        time.sleep(2)
        
    if not job_completed:
        print("Job did not complete in time.")
        return
        
    # Wait another second for schema inference async completion
    time.sleep(1)
    
    # 7. Retrieve Inferred Schema
    print("Retrieving schema...")
    schema_resp = requests.get(f"{BASE_URL}/api/v1/sources/{source_id}/schema", headers=headers)
    if schema_resp.status_code != 200:
        print("Retrieve schema failed:", schema_resp.text)
        return
        
    schema_data = schema_resp.json()
    print("Schema Data received:")
    print(json.dumps(schema_data, indent=2))
    
    # Asserting types
    fields = schema_data.get("fields", [])
    field_types = {f["fieldName"]: f["inferredType"] for f in fields}
    
    assert field_types.get("Row ID") == "INTEGER", f"Expected Row ID to be INTEGER, got {field_types.get('Row ID')}"
    assert field_types.get("Customer Name") == "FREE_TEXT", f"Expected Customer Name to be FREE_TEXT, got {field_types.get('Customer Name')}"
    assert field_types.get("Ship Mode") == "CATEGORY", f"Expected Ship Mode to be CATEGORY, got {field_types.get('Ship Mode')}"
    assert field_types.get("Order Date") == "DATE", f"Expected Order Date to be DATE, got {field_types.get('Order Date')}"
    assert field_types.get("Sales") == "DECIMAL", f"Expected Sales to be DECIMAL, got {field_types.get('Sales')}"
    print("All field types inferred correctly!")

    # 7b. Retrieve Semantic Mappings and test confirmation/override/ignore
    print("Retrieving semantic mappings generated for source...")
    mappings_resp = requests.get(f"{BASE_URL}/api/v1/sources/{source_id}/mappings", headers=headers)
    if mappings_resp.status_code != 200:
        print("Retrieve mappings failed:", mappings_resp.text)
        return
    mappings = mappings_resp.json()
    print(f"Retrieved {len(mappings)} mappings.")
    print(json.dumps(mappings, indent=2))

    # Assert mappings generated for all 5 fields
    assert len(mappings) == 5, f"Expected 5 mappings, got {len(mappings)}"

    # Let's find one mapping to confirm
    pending_mapping = next((m for m in mappings if m["status"] == "PENDING"), None)
    if pending_mapping:
        m_id = pending_mapping["id"]
        print(f"Confirming mapping {m_id} for field {pending_mapping['sourceFieldName']}...")
        conf_resp = requests.put(f"{BASE_URL}/api/v1/sources/{source_id}/mappings/{m_id}/confirm", headers=headers)
        if conf_resp.status_code != 200:
            print("Confirm mapping failed:", conf_resp.text)
            return
        conf_data = conf_resp.json()
        assert conf_data["status"] == "AUTO_CONFIRMED", f"Expected AUTO_CONFIRMED, got {conf_data['status']}"
        print("Mapping confirmed successfully.")

    # Let's find one mapping to override
    some_mapping = mappings[0]
    m_id = some_mapping["id"]
    print(f"Overriding mapping {m_id} for field {some_mapping['sourceFieldName']}...")
    over_resp = requests.put(
        f"{BASE_URL}/api/v1/sources/{source_id}/mappings/{m_id}/override",
        params={"canonicalEntity": "customers", "canonicalField": "segment"},
        headers=headers
    )
    if over_resp.status_code != 200:
        print("Override mapping failed:", over_resp.text)
        return
    over_data = over_resp.json()
    assert over_data["canonicalEntity"] == "customers", f"Expected customers, got {over_data['canonicalEntity']}"
    assert over_data["canonicalField"] == "segment", f"Expected segment, got {over_data['canonicalField']}"
    assert over_data["status"] == "AUTO_CONFIRMED", f"Expected AUTO_CONFIRMED, got {over_data['status']}"
    print("Mapping overridden successfully.")

    # Let's find one mapping to ignore
    ignored_mapping = next((m for m in mappings if m["status"] == "IGNORED"), None)
    if not ignored_mapping and len(mappings) > 1:
        ignored_mapping = mappings[1]
    if ignored_mapping:
        m_id = ignored_mapping["id"]
        print(f"Ignoring mapping {m_id} for field {ignored_mapping['sourceFieldName']}...")
        ign_resp = requests.put(f"{BASE_URL}/api/v1/sources/{source_id}/mappings/{m_id}/ignore", headers=headers)
        if ign_resp.status_code != 200:
            print("Ignore mapping failed:", ign_resp.text)
            return
        ign_data = ign_resp.json()
        assert ign_data["status"] == "IGNORED", f"Expected IGNORED, got {ign_data['status']}"
        print("Mapping ignored successfully.")
    
    # 8. Test Schema Drift by removing "Sales" column
    csv_v2_path = "superstore_v2.csv"
    with open(csv_v2_path, "w") as f:
        f.write("Row ID,Customer Name,Ship Mode,Order Date\n")
        for i in range(1, 23):
            ship_mode = "Standard Class" if i % 3 == 0 else "First Class" if i % 3 == 1 else "Second Class"
            f.write(f"{i},Customer {i},{ship_mode},2024-03-15\n")
            
    print(f"Generated {csv_v2_path} with 'Sales' column removed.")
    
    # Ingest CSV v2
    print("Uploading CSV v2 for ingestion...")
    with open(csv_v2_path, "rb") as f:
        files = {"file": (os.path.basename(csv_v2_path), f, "text/csv")}
        params = {"sourceId": source_id}
        ingest_resp = requests.post(f"{BASE_URL}/api/v1/ingest/csv", files=files, data=params, headers=headers)
        
    if ingest_resp.status_code not in (200, 202, 201):
        print("Ingestion upload v2 failed:", ingest_resp.status_code, ingest_resp.text)
        return
        
    job_id_v2 = ingest_resp.json()["jobId"]
    print(f"Ingestion job v2 started with ID: {job_id_v2}")
    
    # Poll Ingestion Job v2
    job_completed = False
    for attempt in range(15):
        print(f"Polling job v2 status (attempt {attempt+1})...")
        job_resp = requests.get(f"{BASE_URL}/api/v1/jobs/{job_id_v2}", headers=headers)
        if job_resp.status_code == 200:
            job_status = job_resp.json().get("status")
            print(f"Job Status: {job_status}")
            if job_status == "COMPLETED":
                job_completed = True
                break
            elif job_status in ("FAILED", "ABORTED"):
                print("Job v2 failed or was aborted:", job_resp.text)
                return
        time.sleep(2)
        
    if not job_completed:
        print("Job v2 did not complete in time.")
        return
        
    time.sleep(1)
    
    # 9. Verify Drift and Schema Versions
    print("Retrieving schema version history...")
    history_resp = requests.get(f"{BASE_URL}/api/v1/sources/{source_id}/schema/drift", headers=headers)
    if history_resp.status_code != 200:
        print("Retrieve schema drift history failed:", history_resp.text)
        return
        
    history_data = history_resp.json()
    print("Schema history retrieved:")
    print(json.dumps(history_data, indent=2))
    
    assert len(history_data) == 2, f"Expected 2 schema versions, got {len(history_data)}"
    
    v1_schema = next(s for s in history_data if s["version"] == 1)
    v2_schema = next(s for s in history_data if s["version"] == 2)
    
    assert v1_schema["status"] == "SUPERSEDED", f"Expected version 1 status to be SUPERSEDED, got {v1_schema['status']}"
    assert v2_schema["status"] == "ACTIVE", f"Expected version 2 status to be ACTIVE, got {v2_schema['status']}"
    
    v2_fields = {f["fieldName"]: f["inferredType"] for f in v2_schema.get("fields", [])}
    assert "Sales" not in v2_fields, "Expected 'Sales' field to be removed in version 2 schema"
    assert v2_fields.get("Ship Mode") == "CATEGORY", f"Expected Ship Mode to be CATEGORY, got {v2_fields.get('Ship Mode')}"

    # 10. Test Phase 5 Quality Engine and REST Endpoints
    print("=== STARTING PHASE 5 QUALITY ENGINE E2E CHECKS ===")
    csv_v3_path = "superstore_v3.csv"
    with open(csv_v3_path, "w") as f:
        f.write("Row ID,Customer Name,Ship Mode,Order Date,Sales,Country,Currency\n")
        f.write("1,Acme Corp,Standard Class,2024-03-15,100.00,US,USD\n")          # Valid
        f.write("2,,Standard Class,2024-03-15,200.00,US,USD\n")                  # Empty Customer Name (CRITICAL Completeness)
        f.write("3,Globex,First Class,2024-03-15,-50.00,US,USD\n")                 # Negative Sales (HIGH Validity)
        f.write("4,Initech,Second Class,2010-01-01,150.00,US,USD\n")               # Stale Date (MEDIUM Timeliness)
        f.write("5,Umbrella,Standard Class,2024-03-15,300.00,Atlantis,XYZ\n")      # Invalid Country/Currency (LOW Accuracy)

    print(f"Generated {csv_v3_path} with deliberate quality issues.")

    # Ingest CSV v3
    print("Uploading CSV v3 for ingestion...")
    with open(csv_v3_path, "rb") as f:
        files = {"file": (os.path.basename(csv_v3_path), f, "text/csv")}
        params = {"sourceId": source_id}
        ingest_resp = requests.post(f"{BASE_URL}/api/v1/ingest/csv", files=files, data=params, headers=headers)

    if ingest_resp.status_code not in (200, 202, 201):
        print("Ingestion upload v3 failed:", ingest_resp.status_code, ingest_resp.text)
        return

    job_id_v3 = ingest_resp.json()["jobId"]
    print(f"Ingestion job v3 started with ID: {job_id_v3}")

    # Poll Ingestion Job v3
    job_completed = False
    for attempt in range(15):
        print(f"Polling job v3 status (attempt {attempt+1})...")
        job_resp = requests.get(f"{BASE_URL}/api/v1/jobs/{job_id_v3}", headers=headers)
        if job_resp.status_code == 200:
            job_status = job_resp.json().get("status")
            print(f"Job Status: {job_status}")
            if job_status == "COMPLETED":
                job_completed = True
                break
            elif job_status in ("FAILED", "ABORTED"):
                print("Job v3 failed or was aborted:", job_resp.text)
                return
        time.sleep(2)

    if not job_completed:
        print("Job v3 did not complete in time.")
        return

    time.sleep(2)  # Give Quality Engine time to finish updates

    # A. Retrieve Quality Issues
    print("Querying quality issues endpoint...")
    issues_resp = requests.get(
        f"{BASE_URL}/api/v1/quality/issues",
        params={"sourceId": source_id, "status": "OPEN"},
        headers=headers
    )
    if issues_resp.status_code != 200:
        print("Retrieve quality issues failed:", issues_resp.text)
        return

    issues_data = issues_resp.json()
    issues_list = issues_data.get("content", [])
    print(f"Retrieved {len(issues_list)} open issues.")
    print(json.dumps(issues_data, indent=2))

    # Assert that we captured the expected issues
    assert len(issues_list) >= 1, "Expected at least one quality issue to be recorded"
    
    # Check if there is an empty customer name completeness issue
    has_completeness_issue = any(i["ruleCode"] == "COMPLETENESS_CUSTOMER_NAME" for i in issues_list)
    print("Has COMPLETENESS_CUSTOMER_NAME issue:", has_completeness_issue)

    # Let's find one issue to acknowledge
    target_issue = issues_list[0]
    issue_id = target_issue["id"]
    print(f"Acknowledging issue {issue_id}...")
    ack_resp = requests.put(f"{BASE_URL}/api/v1/quality/issues/{issue_id}/acknowledge", headers=headers)
    if ack_resp.status_code != 200:
        print("Acknowledge issue failed:", ack_resp.text)
        return
    ack_data = ack_resp.json()
    assert ack_data["status"] == "ACKNOWLEDGED", f"Expected ACKNOWLEDGED, got {ack_data['status']}"
    print("Issue acknowledged successfully.")

    # B. Retrieve Quality Scores
    print("Querying quality scores trend endpoint...")
    scores_resp = requests.get(f"{BASE_URL}/api/v1/quality/scores", params={"sourceId": source_id}, headers=headers)
    if scores_resp.status_code != 200:
        print("Retrieve quality scores failed:", scores_resp.text)
        return

    scores_list = scores_resp.json()
    print(f"Retrieved {len(scores_list)} historical scores.")
    print(json.dumps(scores_list, indent=2))

    assert len(scores_list) >= 1, "Expected at least one score trend entry"
    latest_score = scores_list[0]
    print(f"Latest Ingestion Job overall score: {latest_score['scoreOverall']}, grade: {latest_score['letterGrade']}")

    print("=== ALL E2E VERIFICATION CHECKS PASSED SUCCESSFULLY! ===")

    # Cleanup temp files
    if os.path.exists(csv_v1_path):
        os.remove(csv_v1_path)
    if os.path.exists(csv_v2_path):
        os.remove(csv_v2_path)
    if os.path.exists(csv_v3_path):
        os.remove(csv_v3_path)

if __name__ == "__main__":
    main()
