import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { colors, radius, spacing, typography } from '../theme';
import { ModelInfo } from '../types/models';

interface Props {
  visible: boolean;
  loading: boolean;
  models: ModelInfo[];
  /** A custom model that exists in storage but wasn't returned by /models. */
  legacyCustomModel?: string;
  selectedId: string;
  onSelect: (model: ModelInfo | { id: string; isCustom: true }) => void;
  onClose: () => void;
  onRefresh: () => void;
}

/**
 * Bottom-sheet style picker. Lists all models returned by the provider,
 * supports search, falls back to a "Custom Model" entry that opens a
 * controlled text input.
 */
export const ModelPickerSheet: React.FC<Props> = ({
  visible,
  loading,
  models,
  legacyCustomModel,
  selectedId,
  onSelect,
  onClose,
  onRefresh,
}) => {
  const [query, setQuery] = useState('');
  const [showCustomInput, setShowCustomInput] = useState(false);
  const [customDraft, setCustomDraft] = useState('');

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return models;
    return models.filter((m) => {
      return (
        m.id.toLowerCase().includes(q) ||
        m.displayName.toLowerCase().includes(q) ||
        (m.ownedBy ?? '').toLowerCase().includes(q) ||
        (m.description ?? '').toLowerCase().includes(q)
      );
    });
  }, [models, query]);

  const showLegacy =
    !!legacyCustomModel &&
    !models.some((m) => m.id === legacyCustomModel);

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <View style={styles.backdrop}>
        <Pressable style={styles.backdropTouch} onPress={onClose} />
        <View
          style={styles.sheet}
          accessibilityViewIsModal
          accessibilityLabel="Select model"
        >
          <View style={styles.handle} />
          <View style={styles.header}>
            <Text style={styles.title}>Select Model</Text>
            <Pressable
              onPress={onRefresh}
              accessibilityRole="button"
              accessibilityLabel="Refresh model list"
              disabled={loading}
              style={({ pressed }) => [
                styles.refreshIconBtn,
                pressed && !loading ? { opacity: 0.6 } : null,
              ]}
            >
              <Text style={styles.refreshText}>↻</Text>
            </Pressable>
          </View>

          <TextInput
            placeholder="Search models"
            placeholderTextColor={colors.textMuted}
            value={query}
            onChangeText={setQuery}
            style={styles.search}
            accessibilityLabel="Search models"
            autoCorrect={false}
            autoCapitalize="none"
          />

          {loading ? (
            <View style={styles.center}>
              <ActivityIndicator color={colors.accent} />
              <Text style={styles.centerText}>Loading models…</Text>
            </View>
          ) : (
            <FlatList
              data={filtered}
              keyExtractor={(m) => m.id}
              keyboardShouldPersistTaps="handled"
              ListHeaderComponent={
                showLegacy ? (
                  <Pressable
                    style={[
                      styles.item,
                      legacyCustomModel === selectedId && styles.itemSelected,
                    ]}
                    onPress={() => {
                      onSelect({ id: legacyCustomModel!, isCustom: true });
                    }}
                    accessibilityRole="button"
                    accessibilityLabel={`Use legacy custom model ${legacyCustomModel}`}
                  >
                    <Text style={styles.itemTitle}>{legacyCustomModel}</Text>
                    <Text style={styles.itemMeta}>Legacy / Custom (not in /models)</Text>
                    {legacyCustomModel === selectedId && (
                      <Text style={styles.check}>✓</Text>
                    )}
                  </Pressable>
                ) : null
              }
              renderItem={({ item }) => {
                const selected = item.id === selectedId;
                return (
                  <Pressable
                    style={[styles.item, selected && styles.itemSelected]}
                    onPress={() => onSelect(item)}
                    accessibilityRole="button"
                    accessibilityLabel={`Select ${item.displayName}`}
                    accessibilityState={{ selected }}
                  >
                    <View style={{ flex: 1 }}>
                      <Text style={styles.itemTitle}>{item.displayName}</Text>
                      {item.id !== item.displayName && (
                        <Text style={styles.itemId}>{item.id}</Text>
                      )}
                      <Text style={styles.itemMeta}>
                        {describeCapabilities(item)}
                      </Text>
                    </View>
                    {selected && <Text style={styles.check}>✓</Text>}
                  </Pressable>
                );
              }}
              ListEmptyComponent={
                <View style={styles.center}>
                  <Text style={styles.centerText}>
                    {query ? 'No models found' : 'No models available'}
                  </Text>
                </View>
              }
              ListFooterComponent={
                <Pressable
                  style={[styles.item, styles.customItem]}
                  onPress={() => {
                    setCustomDraft('');
                    setShowCustomInput((v) => !v);
                  }}
                  accessibilityRole="button"
                  accessibilityLabel="Enter a custom model identifier"
                >
                  <Text style={styles.itemTitle}>Custom Model…</Text>
                  <Text style={styles.itemMeta}>
                    Manually enter a model ID
                  </Text>
                </Pressable>
              }
              contentContainerStyle={{ paddingBottom: spacing.xxl }}
            />
          )}

          {showCustomInput && (
            <View style={styles.customInputWrap}>
              <TextInput
                placeholder="model-id"
                placeholderTextColor={colors.textMuted}
                value={customDraft}
                onChangeText={setCustomDraft}
                style={styles.customInput}
                autoCapitalize="none"
                autoCorrect={false}
                accessibilityLabel="Custom model identifier"
              />
              <Pressable
                style={({ pressed }) => [
                  styles.customConfirm,
                  (!customDraft.trim() || pressed) && { opacity: 0.6 },
                ]}
                disabled={!customDraft.trim()}
                onPress={() => {
                  const id = customDraft.trim();
                  if (!id) return;
                  onSelect({ id, isCustom: true });
                }}
                accessibilityRole="button"
                accessibilityLabel="Use this custom model"
              >
                <Text style={styles.customConfirmText}>Use</Text>
              </Pressable>
            </View>
          )}
        </View>
      </View>
    </Modal>
  );
};

function describeCapabilities(m: ModelInfo): string {
  const caps: string[] = [];
  const mods = m.capabilities?.modalities ?? [];
  if (mods.includes('text')) caps.push('Chat');
  if (m.capabilities?.vision || mods.includes('image')) caps.push('Vision');
  if (m.capabilities?.reasoning) caps.push('Reasoning');
  if (m.capabilities?.tools) caps.push('Tools');
  if (m.contextLength) caps.push(`${formatContext(m.contextLength)} ctx`);
  if (m.ownedBy) caps.push(m.ownedBy);
  return caps.length > 0 ? caps.join(' · ') : 'Chat / Text';
}

function formatContext(n: number): string {
  if (n >= 1_000_000) return `${Math.round(n / 100_000) / 10}M`;
  if (n >= 1000) return `${Math.round(n / 1000)}k`;
  return String(n);
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: colors.overlay,
    justifyContent: 'flex-end',
  },
  backdropTouch: {
    ...(StyleSheet as any).absoluteFillObject,
  },
  sheet: {
    backgroundColor: colors.bgSheet,
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
    paddingBottom: spacing.lg,
    maxHeight: '85%',
  },
  handle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.borderStrong,
    alignSelf: 'center',
    marginBottom: spacing.sm,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  title: {
    ...typography.h1,
  },
  refreshIconBtn: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    backgroundColor: colors.bgChip,
    alignItems: 'center',
    justifyContent: 'center',
  },
  refreshText: {
    color: colors.text,
    fontSize: 18,
  },
  search: {
    backgroundColor: colors.bgInput,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    color: colors.text,
    marginBottom: spacing.sm,
  },
  center: {
    paddingVertical: spacing.xl,
    alignItems: 'center',
  },
  centerText: {
    ...typography.small,
    marginTop: spacing.sm,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    backgroundColor: colors.bgElevated,
    borderRadius: radius.md,
    marginBottom: spacing.sm,
  },
  itemSelected: {
    backgroundColor: colors.accentSoft,
    borderWidth: 1,
    borderColor: colors.accent,
  },
  itemTitle: {
    ...typography.body,
    fontWeight: '600',
  },
  itemId: {
    ...typography.tiny,
    color: colors.textMuted,
    marginTop: 2,
  },
  itemMeta: {
    ...typography.small,
    marginTop: 4,
  },
  check: {
    color: colors.accent,
    fontSize: 18,
    marginLeft: spacing.sm,
    fontWeight: '700',
  },
  customItem: {
    borderWidth: 1,
    borderColor: colors.border,
    borderStyle: 'dashed',
  },
  customInputWrap: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.sm,
  },
  customInput: {
    flex: 1,
    backgroundColor: colors.bgInput,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    color: colors.text,
  },
  customConfirm: {
    paddingHorizontal: spacing.lg,
    backgroundColor: colors.accent,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  customConfirmText: {
    color: '#0B0D12',
    fontWeight: '700',
  },
});
