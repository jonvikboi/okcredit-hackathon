# Database Configuration and Data Alignment

The goal is to ensure the app can connect to MongoDB Atlas without crashing (by avoiding SRV) and that it correctly displays existing stock by matching the database schema.

## Proposed Changes

### Configuration
1. **.env**: Update `MONGODB_URI` to use the standard `mongodb://` format.

### Data Mapping
1. **MongoRepository.kt**: Ensure all field mappings match the actual documents in Atlas.

## User Review Required
- **Standard Connection String**: User needs to provide the `mongodb://` (non-srv) string from Atlas.
- **Sample Document**: User needs to provide one example of a product from the database to verify field names.
