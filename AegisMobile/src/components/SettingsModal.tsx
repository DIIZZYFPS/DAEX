import React from 'react';
import {
  TouchableOpacity,
  TouchableWithoutFeedback,
  StyleSheet,
  ScrollView,
  View,
} from 'react-native';
import { YStack, XStack, Text } from 'tamagui';

interface Props {
  visible: boolean;
  onClose: () => void;
}

interface ModelOption {
  id: string;
  name: string;
  subtitle: string;
  isActive: boolean;
}

const MODELS: ModelOption[] = [
  { id: 'gemma4e4b', name: 'Gemma 4 E4B', subtitle: 'On-Device • Hexagon NPU', isActive: true },
  { id: 'gemma4e2b', name: 'Gemma 4 E2B', subtitle: 'On-Device • Lite', isActive: false },
  { id: 'cloud', name: 'Cloud Fallback', subtitle: 'API • Requires Network', isActive: false },
];

export const SettingsModal: React.FC<Props> = ({ visible, onClose }) => {
  if (!visible) return null;

  return (
    <View style={StyleSheet.absoluteFill}>
      {/* Backdrop */}
      <TouchableWithoutFeedback onPress={onClose}>
        <View style={styles.backdrop} />
      </TouchableWithoutFeedback>

      {/* Modal Card */}
      <View style={styles.centerContainer}>
        <YStack style={styles.modal}>
          <ScrollView showsVerticalScrollIndicator={false}>
            {/* Header */}
            <XStack ai="center" jc="space-between" mb="$4">
              <Text color="#ffffff" fontSize={18} fontWeight="bold" letterSpacing={1}>
                Settings
              </Text>
              <TouchableOpacity onPress={onClose} hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}>
                <Text color="rgba(255, 255, 255, 0.5)" fontSize={20}>✕</Text>
              </TouchableOpacity>
            </XStack>

            {/* Model Selection */}
            <YStack mb="$5">
              <Text color="rgba(255, 255, 255, 0.5)" fontSize={11} fontFamily="monospace" letterSpacing={1} mb="$3">
                MODEL SELECTION
              </Text>
              {MODELS.map((model) => (
                <TouchableOpacity key={model.id} activeOpacity={0.7}>
                  <XStack
                    ai="center"
                    jc="space-between"
                    p="$3"
                    mb="$2"
                    borderRadius="$3"
                    borderWidth={model.isActive ? 1 : 0.5}
                    borderColor={model.isActive ? 'rgba(0, 255, 255, 0.4)' : 'rgba(255, 255, 255, 0.1)'}
                    bg={model.isActive ? 'rgba(0, 255, 255, 0.08)' : 'transparent'}
                  >
                    <YStack>
                      <Text color={model.isActive ? '#00ffff' : '#ffffff'} fontSize={14} fontWeight="600">
                        {model.name}
                      </Text>
                      <Text color="rgba(255, 255, 255, 0.4)" fontSize={11} fontFamily="monospace" mt="$1">
                        {model.subtitle}
                      </Text>
                    </YStack>
                    {model.isActive && (
                      <Text color="#00ffff" fontSize={16}>✓</Text>
                    )}
                  </XStack>
                </TouchableOpacity>
              ))}
            </YStack>

            {/* Theme */}
            <YStack mb="$5">
              <Text color="rgba(255, 255, 255, 0.5)" fontSize={11} fontFamily="monospace" letterSpacing={1} mb="$3">
                THEME
              </Text>
              <XStack
                ai="center"
                p="$3"
                borderRadius="$3"
                borderWidth={1}
                borderColor="rgba(0, 255, 255, 0.4)"
                bg="rgba(0, 255, 255, 0.08)"
              >
                <YStack f={1}>
                  <Text color="#00ffff" fontSize={14} fontWeight="600">
                    OLED Dark
                  </Text>
                  <Text color="rgba(255, 255, 255, 0.4)" fontSize={11} fontFamily="monospace" mt="$1">
                    Optimized for AMOLED displays
                  </Text>
                </YStack>
                <Text color="#00ffff" fontSize={16}>✓</Text>
              </XStack>
            </YStack>

            {/* Data Management */}
            <YStack mb="$5">
              <Text color="rgba(255, 255, 255, 0.5)" fontSize={11} fontFamily="monospace" letterSpacing={1} mb="$3">
                DATA MANAGEMENT
              </Text>
              <TouchableOpacity activeOpacity={0.7}>
                <XStack
                  ai="center"
                  p="$3"
                  borderRadius="$3"
                  borderWidth={0.5}
                  borderColor="rgba(239, 68, 68, 0.3)"
                  bg="rgba(239, 68, 68, 0.05)"
                >
                  <Text color="#ef4444" fontSize={14}>
                    Clear all conversations
                  </Text>
                </XStack>
              </TouchableOpacity>
            </YStack>

            {/* Footer */}
            <YStack ai="center" pt="$3" borderTopWidth={0.5} borderTopColor="rgba(255, 255, 255, 0.08)">
              <Text color="rgba(255, 255, 255, 0.25)" fontSize={11} fontFamily="monospace">
                v0.0.1 • Powered by Aegis Engine
              </Text>
            </YStack>
          </ScrollView>
        </YStack>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  modal: {
    width: '100%',
    maxHeight: '80%',
    backgroundColor: 'rgba(10, 10, 20, 0.95)',
    borderRadius: 16,
    borderWidth: 0.5,
    borderColor: 'rgba(255, 255, 255, 0.1)',
    padding: 20,
  },
});
