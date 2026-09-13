```Bash
curl -X POST http://localhost:8080/api/validation/ \
  -H 'Content-Type: text/plain' \
  --data-binary @.gitlab-ci.yml
```
# Local Validation Run
```Bash
curl -X POST http://localhost:8080/api/validation/run/local \
  -H 'Content-Type: text/plain' \
  --data-binary @.gitlab-ci.yml
```

## Get result by ID
```Bash
curl -X GET http://localhost:8080/api/validation/result/21a7e0e1-3504-4737-9b23-5bd457dfbb57/ \
  -H 'Content-Type: text/plain'
```

b039c699-a168-45a4-9ee2-d32eb9e5f284/