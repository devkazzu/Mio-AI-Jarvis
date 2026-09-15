import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
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
import * as SecureStore from 'expo-secure-store';
import { ModelPicker } from '../components/ModelPicker';
import { ModelPickerSheet } from '../components/ModelPickerSheet';
import { ModelService, redactApiKey } from '../services/ModelService';
import { secureStore } from '../services/SecureStore';
import { settingsStore } from '../state/settingsStore';
import { useSettings } from '../state/useSettings';
import { colors, radius, spacing, typography } from '../theme';
import { ModelInfo } from '../types/models';

interface Props {
  modelService: ModelService;
  onClose?: () => void;
}

const DEFAULT_BASE_URL = 'https://api.b.ai/v1';

export const SettingsScreen: React.FC<Props> = ({ modelService, onClose }) => {
  const state = useSettings();

  // Local form state — kept separate from the store to avoid
  // re-rendering every keystroke into the global store.
  const [baseUrl, setBaseUrl] = useState(state.baseUrl || DEFAULT_BASE_URL);
  const [apiKey, setApiKey] = useState('');
  const [hasKey, setHasKey] = useState(state.hasApiKey);
  const [showKey, setShowKey] = useState(false);

  const [loading, setLoading] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);

  // First-load bootstrap: read secure storage into the store.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const storedUrl = (await secureStore.getBaseUrl()) || DEFAULT_BASE_URL;
      const storedKey = await secureStore.getApiKey();
      const storedModel = await secureStore.getSelectedModel();
      const storedModelFlag =
        (await SecureStore.getItemAsync('mio.selectedModelIsCustom')) === '1';
      if (cancelled) return;
      setBaseUrl(storedUrl);
      setHasKey(!!storedKey);
      settingsStore.setState({
        baseUrl: storedUrl,
        hasApiKey: !!storedKey,
        selectedModel: storedModel || '',
        selectedModelIsCustom:
          storedModelFlag && !state.models.some((m) => m.id === storedModel),
      });
      if (storedUrl) {
        try {
          const cached = await modelService.getCachedModels(storedUrl);
          if (cached && cached.length > 0) {
            settingsStore.setState({
              models: cached,
              modelsFromCache: true,
              modelsFetchedAt: 0,
            });
          }
        } catch {
          /* cache is best-effort */
        }
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ----------------------------------------------------------------
  // Actions
  // ----------------------------------------------------------------

  const persistCredentials = useCallback(async () => {
    const trimmedUrl = baseUrl.trim();
    if (!trimmedUrl) {
      settingsStore.setStatus({
        kind: 'error',
        message: 'Base URL is required.',
      });
      return false;
    }
    const sanitized = trimmedUrl.replace(/\/+$/, '');
    await secureStore.setBaseUrl(sanitized);
    if (apiKey.trim()) {
      await secureStore.setApiKey(apiKey.trim());
      setHasKey(true);
      setApiKey(''); // clear from local input after persisting
      settingsStore.setState({ hasApiKey: true, baseUrl: sanitized });
    } else {
      settingsStore.setState({ baseUrl: sanitized });
    }
    return true;
  }, [apiKey, baseUrl]);

  const performFetch = useCallback(
    async (opts: { silent?: boolean } = {}) => {
      const url = baseUrl.trim().replace(/\/+$/, '');
      const key = await secureStore.getApiKey();
      if (!key) {
        settingsStore.setStatus({
          kind: 'error',
          message: 'Save your API key first.',
          code: 'MISSING_API_KEY',
        });
        return;
      }
      setLoading(true);
      settingsStore.setStatus({
        kind: 'loading',
        message: 'Loading models…',
      });
      try {
        const models = await modelService.fetchModels(url, key);
        const prevSelected = state.selectedModel;
        const stillExists = models.some((m) => m.id === prevSelected);
        const nextSelected = stillExists
          ? prevSelected
          : models[0]?.id ?? '';
        settingsStore.setState({
          models,
          modelsFromCache: false,
          modelsFetchedAt: Date.now(),
          selectedModel: nextSelected,
          selectedModelIsCustom: false,
          status: {
            kind: 'success',
            message: opts.silent
              ? `Loaded ${models.length} models.`
              : `Loaded ${models.length} models.`,
          },
        });
        if (nextSelected) {
          await secureStore.setSelectedModel(nextSelected, false);
        }
      } catch (e: any) {
        // Fall back to cache.
        const cached = await modelService.getCachedModels(url).catch(() => null);
        if (cached && cached.length > 0) {
          settingsStore.setState({
            models: cached,
            modelsFromCache: true,
            status: {
              kind: 'error',
              message:
                'Could not refresh models. Showing cached list (offline).',
              code: e?.code,
            },
          });
        } else {
          settingsStore.setStatus({
            kind: 'error',
            message: e?.message || 'Failed to fetch models.',
            code: e?.code,
          });
        }
      } finally {
        setLoading(false);
      }
    },
    [baseUrl, modelService, state.selectedModel],
  );

  const handleSave = useCallback(async () => {
    const ok = await persistCredentials();
    if (!ok) return;
    await performFetch({ silent: false });
  }, [persistCredentials, performFetch]);

  const handleTestConnection = useCallback(async () => {
    const trimmedUrl = baseUrl.trim().replace(/\/+$/, '');
    let key = apiKey.trim();
    if (!key) key = (await secureStore.getApiKey()) || '';
    if (!trimmedUrl) {
      settingsStore.setStatus({
        kind: 'error',
        message: 'Base URL is required.',
      });
      return;
    }
    setLoading(true);
    settingsStore.setStatus({
      kind: 'loading',
      message: 'Testing connection…',
    });
    const result = await modelService.testConnection(trimmedUrl, key);
    setLoading(false);
    if (result.ok) {
      settingsStore.setStatus({
        kind: 'success',
        message: `Connected. Models available: ${result.modelCount}`,
      });
      // Persist latest credentials if a new key was supplied.
      if (apiKey.trim()) {
        await persistCredentials();
      }
      // Refresh models too.
      await performFetch({ silent: true });
    } else {
      settingsStore.setStatus({
        kind: 'error',
        message: `Offline — ${result.error ?? 'Connection failed'}`,
        code: result.errorCode,
      });
    }
  }, [apiKey, baseUrl, modelService, persistCredentials, performFetch]);

  const handleSelectModel = useCallback(
    async (m: ModelInfo | { id: string; isCustom: true }) => {
      await secureStore.setSelectedModel(m.id, 'isCustom' in m);
      settingsStore.setState({
        selectedModel: m.id,
        selectedModelIsCustom: 'isCustom' in m,
      });
      setPickerOpen(false);
    },
    [],
  );

  const handleClearKey = useCallback(() => {
    Alert.alert(
      'Clear API key?',
      'This removes the stored key from this device.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Clear',
          style: 'destructive',
          onPress: async () => {
            await secureStore.clearApiKey();
            setHasKey(false);
            settingsStore.setState({ hasApiKey: false });
          },
        },
      ],
    );
  }, []);

  // ----------------------------------------------------------------
  // Render
  // ----------------------------------------------------------------

  const status = state.status;

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View style={styles.headerBar}>
          <Text style={styles.headerTitle}>AI Settings</Text>
          {onClose ? (
            <Pressable
              onPress={onClose}
              accessibilityRole="button"
              accessibilityLabel="Close settings"
              style={({ pressed }) => [
                styles.closeBtn,
                pressed && { opacity: 0.6 },
              ]}
            >
              <Text style={styles.closeText}>Done</Text>
            </Pressable>
          ) : null}
        </View>
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
        >
          {/* Provider */}
          <Section title="PROVIDER">
            <Label>Base URL</Label>
            <TextInput
              style={styles.input}
              value={baseUrl}
              onChangeText={setBaseUrl}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
              placeholder={DEFAULT_BASE_URL}
              placeholderTextColor={colors.textMuted}
              accessibilityLabel="Base URL"
            />
            <Helper>Use any OpenAI-compatible endpoint (e.g. b.ai, OpenAI, vLLM).</Helper>
          </Section>

          {/* API key */}
          <Section title="API KEY">
            <Label>API key</Label>
            <View style={styles.keyRow}>
              <TextInput
                style={[styles.input, { flex: 1 }]}
                value={apiKey}
                onChangeText={setApiKey}
                placeholder={hasKey ? '•••••••••••• (saved)' : 'Paste your API key'}
                placeholderTextColor={colors.textMuted}
                secureTextEntry={!showKey}
                autoCapitalize="none"
                autoCorrect={false}
                accessibilityLabel="API key"
              />
              <Pressable
                onPress={() => setShowKey((v) => !v)}
                style={({ pressed }) => [
                  styles.eyeBtn,
                  pressed && { opacity: 0.6 },
                ]}
                accessibilityRole="button"
                accessibilityLabel={showKey ? 'Hide API key' : 'Show API key'}
              >
                <Text style={styles.eyeText}>{showKey ? '🙈' : '👁'}</Text>
              </Pressable>
            </View>
            <View style={styles.rowBetween}>
              <Helper>
                {hasKey
                  ? `A key is stored on this device (${redactApiKey('stored')}).`
                  : 'Stored encrypted on device.'}
              </Helper>
              {hasKey && (
                <Pressable
                  onPress={handleClearKey}
                  accessibilityRole="button"
                  accessibilityLabel="Clear stored API key"
                >
                  <Text style={styles.linkDanger}>Clear key</Text>
                </Pressable>
              )}
            </View>
            <PrimaryButton label="SAVE & FETCH MODELS" onPress={handleSave} />
            <SecondaryButton
              label={loading ? 'TESTING…' : 'TEST CONNECTION'}
              onPress={handleTestConnection}
              disabled={loading}
              loading={loading}
            />
          </Section>

          {/* Model */}
          <Section title="MODEL">
            <ModelPicker
              value={state.selectedModel}
              placeholder="Select a model"
              loading={loading}
              onPress={() => setPickerOpen(true)}
              onRefresh={() => performFetch({ silent: false })}
              cached={state.modelsFromCache}
            />
            {state.modelsFromCache && (
              <Text style={styles.cachedHint}>
                Showing previously cached models. Pull to refresh once you're back online.
              </Text>
            )}
          </Section>

          {/* Status */}
          {status.kind !== 'idle' && (
            <View
              style={[
                styles.statusBanner,
                status.kind === 'error' && styles.statusBannerError,
                status.kind === 'success' && styles.statusBannerSuccess,
                status.kind === 'loading' && styles.statusBannerLoading,
              ]}
              accessibilityLiveRegion="polite"
            >
              <Text
                style={[
                  styles.statusText,
                  status.kind === 'error' && { color: colors.danger },
                  status.kind === 'success' && { color: colors.success },
                ]}
              >
                {status.message}
              </Text>
            </View>
          )}

          <Text style={styles.footerNote}>
            Mio connects to any OpenAI-compatible provider. Your API key never
            leaves your device except when calling the configured endpoint.
          </Text>
        </ScrollView>
      </KeyboardAvoidingView>

      <ModelPickerSheet
        visible={pickerOpen}
        loading={loading}
        models={state.models}
        legacyCustomModel={
          state.selectedModelIsCustom ? state.selectedModel : undefined
        }
        selectedId={state.selectedModel}
        onSelect={handleSelectModel}
        onClose={() => setPickerOpen(false)}
        onRefresh={() => performFetch({ silent: false })}
      />

    </SafeAreaView>
  );
};

// ----------------------------------------------------------------------
// Small subcomponents
// ----------------------------------------------------------------------

const Section: React.FC<React.PropsWithChildren<{ title: string }>> = ({
  title,
  children,
}) => (
  <View style={styles.section}>
    <Text style={styles.sectionTitle}>{title}</Text>
    <View style={styles.sectionBody}>{children}</View>
  </View>
);

const Label: React.FC<React.PropsWithChildren<{}>> = ({ children }) => (
  <Text style={styles.label}>{children}</Text>
);

const Helper: React.FC<React.PropsWithChildren<{}>> = ({ children }) => (
  <Text style={styles.helper}>{children}</Text>
);

const PrimaryButton: React.FC<{ label: string; onPress: () => void }> = ({
  label,
  onPress,
}) => (
  <Pressable
    onPress={onPress}
    accessibilityRole="button"
    style={({ pressed }) => [
      styles.primaryBtn,
      pressed && { opacity: 0.85 },
    ]}
  >
    <Text style={styles.primaryBtnText}>{label}</Text>
  </Pressable>
);

const SecondaryButton: React.FC<{
  label: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
}> = ({ label, onPress, disabled, loading }) => (
  <Pressable
    onPress={onPress}
    disabled={disabled}
    accessibilityRole="button"
    accessibilityState={{ disabled: !!disabled, busy: !!loading }}
    style={({ pressed }) => [
      styles.secondaryBtn,
      pressed && !disabled && { opacity: 0.85 },
      disabled && { opacity: 0.5 },
    ]}
  >
    {loading ? (
      <ActivityIndicator color={colors.text} size="small" />
    ) : (
      <Text style={styles.secondaryBtnText}>{label}</Text>
    )}
  </Pressable>
);

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  headerTitle: {
    ...typography.h1,
  },
  closeBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.md,
  },
  closeText: {
    color: colors.accent,
    fontWeight: '600',
  },
  content: {
    padding: spacing.lg,
    paddingBottom: spacing.xxl,
    gap: spacing.lg,
  },
  section: {
    gap: spacing.sm,
  },
  sectionTitle: {
    ...typography.tiny,
    letterSpacing: 1,
    color: colors.textMuted,
    marginBottom: spacing.xs,
  },
  sectionBody: {
    gap: spacing.sm,
  },
  label: {
    ...typography.small,
    color: colors.textSecondary,
  },
  helper: {
    ...typography.tiny,
    color: colors.textMuted,
  },
  input: {
    backgroundColor: colors.bgInput,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    color: colors.text,
    minHeight: 44,
  },
  keyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  eyeBtn: {
    width: 44,
    height: 44,
    borderRadius: radius.md,
    backgroundColor: colors.bgChip,
    alignItems: 'center',
    justifyContent: 'center',
  },
  eyeText: {
    fontSize: 18,
  },
  rowBetween: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  linkDanger: {
    color: colors.danger,
    fontSize: 12,
    fontWeight: '600',
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.sm,
  },
  primaryBtn: {
    backgroundColor: colors.accent,
    paddingVertical: spacing.md,
    borderRadius: radius.md,
    alignItems: 'center',
    marginTop: spacing.sm,
    minHeight: 48,
    justifyContent: 'center',
  },
  primaryBtnText: {
    color: '#0B0D12',
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  secondaryBtn: {
    backgroundColor: colors.bgChip,
    paddingVertical: spacing.md,
    borderRadius: radius.md,
    alignItems: 'center',
    minHeight: 48,
    justifyContent: 'center',
  },
  secondaryBtnText: {
    color: colors.text,
    fontWeight: '600',
    letterSpacing: 0.5,
  },
  statusBanner: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.bgElevated,
    borderWidth: 1,
    borderColor: colors.border,
  },
  statusBannerError: {
    borderColor: colors.danger,
    backgroundColor: colors.dangerSoft,
  },
  statusBannerSuccess: {
    borderColor: colors.success,
    backgroundColor: colors.successSoft,
  },
  statusBannerLoading: {
    borderColor: colors.accent,
    backgroundColor: colors.accentSoft,
  },
  statusText: {
    ...typography.small,
  },
  cachedHint: {
    ...typography.tiny,
    color: colors.warning,
    marginTop: spacing.xs,
  },
  footerNote: {
    ...typography.tiny,
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: spacing.lg,
  },
});
