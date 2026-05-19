# Примеры JSON-запросов

## 1) Регистрация

```json
{
  "commandType": "REGISTER",
  "payload": {
    "username": "admin",
    "password": "admin123",
    "role": "ADMIN"
  }
}
```

## 2) Логин

```json
{
  "commandType": "LOGIN",
  "payload": {
    "username": "admin",
    "password": "admin123"
  }
}
```

## 3) Создание поставщика

```json
{
  "commandType": "CREATE_SUPPLIER",
  "authToken": "<TOKEN>",
  "payload": {
    "name": "BelSupply",
    "score": 83.5
  }
}
```

## 4) Создание контракта

```json
{
  "commandType": "CREATE_CONTRACT",
  "authToken": "<TOKEN>",
  "payload": {
    "number": "C-2026-001",
    "supplierId": 1,
    "dueDate": "2026-06-01",
    "amount": 120000,
    "quantityUnits": 100000
  }
}
```

## 5) Расчет риска

```json
{
  "commandType": "CALCULATE_CONTRACT_RISK",
  "authToken": "<TOKEN>",
  "payload": {
    "contractId": 1
  }
}
```
