package app.toetoetoe.luz;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle savedInstanceState) {
        registerPlugin(HomeAssistantPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
