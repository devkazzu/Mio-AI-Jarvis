import React, { useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaView, StyleSheet, View } from 'react-native';
import { ChatService } from './src/services/ChatService';
import { ModelService } from './src/services/ModelService';
import { ChatScreen } from './src/screens/ChatScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { colors } from './src/theme';

/**
 * Application root.
 *
 * Two screens — Chat and Settings — toggled via in-app state. The service
 * singletons (`ModelService`, `ChatService`) live at the root so the same
 * instances are reused if/when navigation grows.
 */
export default function App() {
  const [modelService] = useState(() => new ModelService());
  const [chatService] = useState(() => new ChatService());
  const [showSettings, setShowSettings] = useState(true);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar style="light" />
      <View style={styles.container}>
        {showSettings ? (
          <SettingsScreen
            modelService={modelService}
            onClose={() => setShowSettings(false)}
          />
        ) : (
          <ChatScreen
            chatService={chatService}
            onOpenSettings={() => setShowSettings(true)}
          />
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  container: {
    flex: 1,
  },
});
