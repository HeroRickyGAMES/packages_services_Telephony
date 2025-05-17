Location:
packages/services/Telephony

This code removes voicemail notification forever and ever to never show this shit notification again!!!!!!


How to remove Voicemail notification?

First go to the file:
packages/services/Telephony/src/com/android/phone/NotificationMgr.java

Search for void updateMwi(int subId, boolean visible, boolean isRefresh)

in the method pass this:

```java
  void updateMwi(int subId, boolean visible, boolean isRefresh) {
            Log.i(LOG_TAG, "Voicemail notification suprimida por personalização.");
            return;
    }
```

And that's it, the notification will disappear!!!  
If you have problems with the build and the build is complaining about  ```Log.i(LOG_TAG,``` you can remove this line! It is just for debugging!
