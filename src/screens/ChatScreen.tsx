import React, { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native';
import { ChatService, ChatMessage } from '../services/ChatService';
import { secureStore } from '../services/SecureStore';
import { useSettings } from '../state/useSettings';
import { colors, radius, spacing, typography } from '../theme';

interface Props {
  chatService: ChatService;
  onOpenSettings: () => void;
}

export const ChatScreen: React.FC<Props> = ({ chatService, onOpenSettings }) => {
  const state = useSettings();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const hasConfig = !!state.baseUrl && state.hasApiKey && !!state.selectedModel;

  const handleSend = useCallback(async () => {
    const text = draft.trim();
    if (!text || sending) return;
    if (!hasConfig) {
      setError('Open settings and configure Base URL, API key and a model.');
      return;
    }
    setError(null);
    const userMsg: ChatMessage = { role: 'user', content: text };
    const nextMessages = [...messages, userMsg];
    setMessages(nextMessages);
    setDraft('');
    setSending(true);
    try {
      const apiKey = (await secureStore.getApiKey()) ?? '';
      const result = await chatService.complete({
        baseUrl: state.baseUrl,
        apiKey,
        model: state.selectedModel,
        messages: nextMessages,
      });
      setMessages((cur) => [
        ...cur,
        { role: 'assistant', content: result.text || '(empty response)' },
      ]);
    } catch (e: any) {
      setError(e?.message || 'Chat failed.');
    } finally {
      setSending(false);
    }
  }, [draft, sending, hasConfig, chatService, messages, state.baseUrl, state.selectedModel]);

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.headerBar}>
        <View style={{ flex: 1 }}>
          <Text style={styles.headerTitle}>Mio</Text>
          <Text style={styles.headerSubtitle} numberOfLines={1}>
            {state.selectedModel
              ? `${state.selectedModel}${state.selectedModelIsCustom ? ' (custom)' : ''}`
              : 'No model selected'}
          </Text>
        </View>
        <Pressable
          onPress={onOpenSettings}
          accessibilityRole="button"
          accessibilityLabel="Open settings"
          style={({ pressed }) => [
            styles.settingsBtn,
            pressed && { opacity: 0.6 },
          ]}
        >
          <Text style={styles.settingsBtnText}>Settings</Text>
        </Pressable>
      </View>

      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.messages}
          keyboardShouldPersistTaps="handled"
        >
          {messages.length === 0 ? (
            <View style={styles.empty}>
              <Text style={styles.emptyTitle}>Hi, I'm Mio.</Text>
              <Text style={styles.emptySubtitle}>
                {hasConfig
                  ? 'Send a message to get started.'
                  : 'Open Settings to configure your provider, API key and model.'}
              </Text>
            </View>
          ) : (
            messages.map((m, i) => (
              <View
                key={i}
                style={[
                  styles.bubble,
                  m.role === 'user' ? styles.bubbleUser : styles.bubbleAssistant,
                ]}
              >
                <Text style={styles.bubbleRole}>
                  {m.role === 'user' ? 'You' : 'Mio'}
                </Text>
                <Text style={styles.bubbleText}>{m.content}</Text>
              </View>
            ))
          )}
          {error && (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}
        </ScrollView>

        <View style={styles.inputBar}>
          <TextInput
            style={styles.input}
            placeholder={
              hasConfig ? 'Message Mio…' : 'Configure settings first…'
            }
            placeholderTextColor={colors.textMuted}
            value={draft}
            onChangeText={setDraft}
            editable={hasConfig && !sending}
            multiline
            accessibilityLabel="Message input"
          />
          <Pressable
            onPress={handleSend}
            disabled={!hasConfig || !draft.trim() || sending}
            accessibilityRole="button"
            accessibilityLabel="Send message"
            style={({ pressed }) => [
              styles.sendBtn,
              (!hasConfig || !draft.trim() || sending) && { opacity: 0.4 },
              pressed && { opacity: 0.8 },
            ]}
          >
            {sending ? (
              <ActivityIndicator color="#0B0D12" size="small" />
            ) : (
              <Text style={styles.sendBtnText}>↑</Text>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  headerTitle: {
    ...typography.h1,
  },
  headerSubtitle: {
    ...typography.tiny,
    color: colors.textMuted,
    marginTop: 2,
  },
  settingsBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.md,
    backgroundColor: colors.bgChip,
  },
  settingsBtnText: {
    color: colors.text,
    fontWeight: '600',
  },
  messages: {
    padding: spacing.lg,
    gap: spacing.md,
    flexGrow: 1,
  },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: spacing.xxl * 2,
  },
  emptyTitle: {
    ...typography.h1,
    marginBottom: spacing.sm,
  },
  emptySubtitle: {
    ...typography.body,
    color: colors.textSecondary,
    textAlign: 'center',
    paddingHorizontal: spacing.xl,
  },
  bubble: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    borderRadius: radius.lg,
    maxWidth: '90%',
  },
  bubbleUser: {
    backgroundColor: colors.accent,
    alignSelf: 'flex-end',
  },
  bubbleAssistant: {
    backgroundColor: colors.bgElevated,
    borderWidth: 1,
    borderColor: colors.border,
    alignSelf: 'flex-start',
  },
  bubbleRole: {
    ...typography.tiny,
    color: colors.textSecondary,
    marginBottom: 4,
  },
  bubbleText: {
    color: colors.text,
    fontSize: 15,
    lineHeight: 20,
  },
  errorBox: {
    backgroundColor: colors.dangerSoft,
    borderColor: colors.danger,
    borderWidth: 1,
    borderRadius: radius.md,
    padding: spacing.md,
  },
  errorText: {
    color: colors.danger,
    fontSize: 13,
  },
  inputBar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: spacing.sm,
    padding: spacing.md,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    backgroundColor: colors.bg,
  },
  input: {
    flex: 1,
    backgroundColor: colors.bgInput,
    borderRadius: radius.lg,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    color: colors.text,
    minHeight: 44,
    maxHeight: 120,
  },
  sendBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendBtnText: {
    color: '#0B0D12',
    fontWeight: '700',
    fontSize: 18,
  },
});
