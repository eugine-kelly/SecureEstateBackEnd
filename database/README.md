# SecureEstate Database

## Export
This folder contains the PostgreSQL database export for SecureEstate.

## How to import locally
```bash
createdb secureestate
psql -U postgres -d secureestate -f secureestate_backup.sql
```

## Tables
- users — registered platform users
- properties — property listings
- escrow_transactions — M-Pesa payment records
- password_reset_tokens — password reset links
