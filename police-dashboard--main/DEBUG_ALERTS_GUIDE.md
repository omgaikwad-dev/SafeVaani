# Testing Active Alerts Section - Debug Guide

## 🔍 How to Debug the Active Alerts Section

### Step 1: Open the Application
1. Make sure `npm run dev` is running
2. Open your browser to `http://localhost:5173`
3. Login with: `admin@police.sentinel.gov` / `SecureLogin!2024`

### Step 2: Open Browser Console
- **Chrome/Edge**: Press `F12` or `Ctrl+Shift+I`
- **Firefox**: Press `F12` or `Ctrl+Shift+K`
- Go to the **Console** tab

### Step 3: Check for Firebase Connection Logs
You should see these console messages:

```
🔥 Firebase listeners starting...
🚨 ALERTS UPDATE from Firebase: {Object with your alerts}
🚨 Number of alerts: 1
👥 USERS UPDATE from Firebase: {Object with your users}
👥 Number of users: 7
```

### Step 4: Navigate to Active Alerts Tab
1. Click on "Active Alerts" in the sidebar
2. You should see a **DEBUG INFO panel** at the top showing:
   - `hasAlerts: true`
   - `alertsCount: 1`
   - `alertKeys: ["-OkUnen8V8Cs5qMkkRUk"]`

### Step 5: Verify Alert Display
You should see the alert card showing:
- **Name**: Priya Sharma
- **Emotion**: PANIC
- **Location**: Senapati Bapat Road, Near ICC Trade Tower
- **Phone**: +91 98765 43210
- **Status**: ACTIVE (with red background)
- **Confidence**: 94.2%

## 🚨 Common Issues & Solutions

### Issue 1: "No SOS Alerts Detected" message
**Cause**: Firebase data not loading
**Solution**: 
- Check console for Firebase errors
- Verify Firebase database URL is correct
- Check Firebase security rules allow read access

### Issue 2: Console shows "Number of alerts: 0"
**Cause**: Alerts path is empty in Firebase
**Solution**:
- Go to Firebase Console
- Navigate to Realtime Database
- Verify `/alerts` path exists with data

### Issue 3: Debug panel shows empty data
**Cause**: Data not being passed to component
**Solution**:
- Check App.tsx console logs
- Verify `dashboardData` state is updating
- Check if user is authenticated

## 📋 Expected Firebase Data Structure

Your Firebase should have this structure:

```json
{
  "alerts": {
    "-OkUnen8V8Cs5qMkkRUk": {
      "confidence": 94.2,
      "emotion": "PANIC",
      "latitude": 18.5204,
      "locationName": "Senapati Bapat Road, Near ICC Trade Tower",
      "longitude": 73.8567,
      "phone": "+91 98765 43210",
      "status": "ACTIVE",
      "threatLevel": "High",
      "timestamp": 1770059864899,
      "userName": "Priya Sharma"
    }
  },
  "users": {
    ...
  }
}
```

## 🔧 Quick Fixes

### If alerts still don't show:

1. **Check Firebase Rules**:
   ```json
   {
     "rules": {
       ".read": true,
       ".write": true
     }
   }
   ```

2. **Verify Database URL** in `firebase.ts`:
   ```typescript
   databaseURL: "https://akatsuki-98705-default-rtdb.firebaseio.com"
   ```

3. **Hard Refresh Browser**:
   - Press `Ctrl+Shift+R` (Windows/Linux)
   - Press `Cmd+Shift+R` (Mac)

## 📸 What You Should See

1. **Header Badge**: "1 Active Emergency" (pulsing red)
2. **Alert Card**: Red border with rose background
3. **Map Buttons**: "Get Directions" and "View on Map"
4. **Debug Panel**: Shows alert count and keys

## ✅ Success Indicators

- ✅ Console shows Firebase connection logs
- ✅ Debug panel shows `alertsCount: 1`
- ✅ Alert card displays with Priya Sharma's information
- ✅ Buttons are clickable and open Google Maps
- ✅ Phone number is clickable (tel: link)

---

**After Testing**: Once you confirm it's working, I'll remove the debug panel for production use.
