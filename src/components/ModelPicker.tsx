import React from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View,
  AccessibilityRole,
} from 'react-native';
import { colors, radius, spacing, typography } from '../theme';

interface Props {
  value: string;
  placeholder: string;
  loading: boolean;
  disabled?: boolean;
  onPress: () => void;
  onRefresh: () => void;
  cached?: boolean;
  accessibilityLabel?: string;
}

/**
 * Compact row that shows the current model and exposes a refresh icon.
 * Tapping the body opens the picker sheet; tapping the refresh icon
 * re-fetches the model list.
 */
export const ModelPicker: React.FC<Props> = ({
  value,
  placeholder,
  loading,
  disabled,
  onPress,
  onRefresh,
  cached,
  accessibilityLabel,
}) => {
  return (
    <View>
      <View style={styles.row}>
        <Pressable
          accessibilityRole={'button' as AccessibilityRole}
          accessibilityLabel={
            accessibilityLabel ??
            (value ? `Selected model ${value}. Tap to change.` : placeholder)
          }
          accessibilityState={{ disabled: !!disabled, busy: loading }}
          onPress={onPress}
          disabled={disabled || loading}
          style={({ pressed }) => [
            styles.field,
            pressed && !disabled && !loading ? styles.fieldPressed : null,
            (disabled || loading) && styles.fieldDisabled,
          ]}
        >
          {loading ? (
            <View style={styles.loadingInline}>
              <ActivityIndicator color={colors.accent} size="small" />
              <Text style={[styles.value, styles.valueLoading]}>
                Loading models…
              </Text>
            </View>
          ) : (
            <>
              <Text
                style={[
                  styles.value,
                  !value && styles.placeholder,
                ]}
                numberOfLines={1}
              >
                {value || placeholder}
              </Text>
              <Text style={styles.chevron}>▼</Text>
            </>
          )}
        </Pressable>
        <Pressable
          accessibilityRole={'button' as AccessibilityRole}
          accessibilityLabel="Refresh model list"
          accessibilityState={{ disabled: !!disabled, busy: loading }}
          onPress={onRefresh}
          disabled={disabled || loading}
          style={({ pressed }) => [
            styles.refreshBtn,
            pressed && !disabled && !loading ? styles.refreshBtnPressed : null,
            (disabled || loading) && styles.fieldDisabled,
          ]}
        >
          <Text style={styles.refreshIcon}>↻</Text>
        </Pressable>
      </View>
      {!!cached && (
        <Text style={styles.cachedNote}>
          Showing cached models — offline.
        </Text>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'stretch',
    gap: spacing.sm,
  },
  field: {
    flex: 1,
    minHeight: 48,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.md,
    backgroundColor: colors.bgInput,
    borderWidth: 1,
    borderColor: colors.border,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  fieldPressed: {
    backgroundColor: colors.bgElevated,
    borderColor: colors.borderStrong,
  },
  fieldDisabled: {
    opacity: 0.6,
  },
  value: {
    ...typography.body,
    flex: 1,
    marginRight: spacing.sm,
  },
  valueLoading: {
    color: colors.textSecondary,
    marginLeft: spacing.sm,
  },
  placeholder: {
    color: colors.textMuted,
  },
  chevron: {
    color: colors.textSecondary,
    fontSize: 12,
  },
  loadingInline: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  refreshBtn: {
    width: 48,
    minHeight: 48,
    borderRadius: radius.md,
    backgroundColor: colors.bgChip,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  refreshBtnPressed: {
    backgroundColor: colors.bgElevated,
    borderColor: colors.borderStrong,
  },
  refreshIcon: {
    color: colors.text,
    fontSize: 20,
    fontWeight: '600',
  },
  cachedNote: {
    ...typography.tiny,
    marginTop: spacing.xs,
    color: colors.warning,
  },
});
