---
sidebar_position: 2
title: Java
---

# Java

```java
public class GameActivity extends AppCompatActivity {
    private static final String ACTION = "se.spacify.app.roaming.event.MOTION";

    private final BroadcastReceiver roaming = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra("motion_type");
            String direction = intent.getStringExtra("direction");
            float intensity = intent.getFloatExtra("intensity", 0f);
            if ("jumping".equals(type)) {
                player.jump(intensity);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(
                this, roaming, new IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(roaming);
        super.onPause();
    }
}
```
