import React from 'react';
import {
  TouchableOpacity,
  TouchableWithoutFeedback,
  StyleSheet,
  ScrollView,
  View,
  Switch,
} from 'react-native';
import { YStack, XStack, Text } from 'tamagui';
import { ModelStatus } from '../hooks/useAegisInference';

interface Props {
  visible: boolean;
  onClose: () => void;
  modelStatus: ModelStatus;
  useGPU: boolean;
  onToggleGPU: (enabled: boolean) => void;
  onDownloadModel: () => void;
  onDeleteModel: () => void;
}

export const SettingsModal: React.FC<Props> = ({
  visible,
  onClose,
  modelStatus,
  useGPU,
  onToggleGPU,
  onDownloadModel,
  onDeleteModel,
}) => {
  if (!visible) return null;

  const isModelReady = modelStatus === 'ready';
  const isModelAvailable =
    modelStatus === 'ready' || modelStatus === 'loading';

  const getModelStatusText = () => {
    switch (modelStatus) {
      case 'ready':
        return 'Loaded • Active';
      case 'loading':
        return 'Loading...';
      case 'downloading':
        return 'Downloading...';
      case 'error':
        return 'Error';
      default:
        return 'Not Downloaded';
    }
  };

  const getModelStatusColor = () => {
    switch (modelStatus) {
      case 'ready':
        return '#4ade80';
      case 'loading':
      case 'downloading':
        return '#f59e0b';
      case 'error':
        return '#ef4444';
      default:
        return 'rgba(255, 255, 255, 0.4)';
    }
  };

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
              <Text
                color="#ffffff"
                fontSize={18}
                fontWeight="bold"
                letterSpacing={1}
              >
                Settings
              </Text>
              <TouchableOpacity
                onPress={onClose}
                hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
              >
                <Text color="rgba(255, 255, 255, 0.5)" fontSize={20}>
                  ✕
                </Text>
              </TouchableOpacity>
            </XStack>

            {/* Model Info */}
            <YStack mb="$5">
              <Text
                color="rgba(255, 255, 255, 0.5)"
                fontSize={11}
                fontFamily="monospace"
                letterSpacing={1}
                mb="$3"
              >
                MODEL
              </Text>
              <YStack
                p="$3"
                borderRadius="$3"
                borderWidth={1}
                borderColor="rgba(0, 255, 255, 0.3)"
                bg="rgba(0, 255, 255, 0.06)"
              >
                <XStack ai="center" jc="space-between">
                  <YStack>
                    <Text
                      color="#00ffff"
                      fontSize={14}
                      fontWeight="600"
                    >
                      Gemma 4 E4B-IT
                    </Text>
                    <Text
                      color="rgba(255, 255, 255, 0.4)"
                      fontSize={11}
                      fontFamily="monospace"
                      mt="$1"
                    >
                      Q4_K_M • ~2.5GB • 4B params
                    </Text>
                  </YStack>
                  <YStack ai="flex-end">
                    <XStack ai="center" gap="$1.5">
                      <View
                        style={[
                          styles.statusDot,
                          { backgroundColor: getModelStatusColor() },
                        ]}
                      />
                      <Text
                        color={getModelStatusColor()}
                        fontSize={11}
                        fontFamily="monospace"
                      >
                        {getModelStatusText()}
                      </Text>
                    </XStack>
                  </YStack>
                </XStack>

                {/* Download button if not downloaded */}
                {modelStatus === 'not_downloaded' && (
                  <TouchableOpacity
                    onPress={onDownloadModel}
                    activeOpacity={0.7}
                    style={{ marginTop: 12 }}
                  >
                    <XStack
                      ai="center"
                      jc="center"
                      py="$2.5"
                      borderRadius="$3"
                      bg="#00ffff"
                    >
                      <Text
                        color="#000000"
                        fontSize={13}
                        fontWeight="bold"
                        fontFamily="monospace"
                      >
                        ↓ DOWNLOAD MODEL
                      </Text>
                    </XStack>
                  </TouchableOpacity>
                )}
              </YStack>
            </YStack>

            {/* Inference Settings */}
            <YStack mb="$5">
              <Text
                color="rgba(255, 255, 255, 0.5)"
                fontSize={11}
                fontFamily="monospace"
                letterSpacing={1}
                mb="$3"
              >
                INFERENCE
              </Text>

              {/* GPU Offload Toggle */}
              <XStack
                ai="center"
                jc="space-between"
                p="$3"
                borderRadius="$3"
                borderWidth={0.5}
                borderColor={
                  useGPU
                    ? 'rgba(168, 85, 247, 0.4)'
                    : 'rgba(255, 255, 255, 0.1)'
                }
                bg={
                  useGPU
                    ? 'rgba(168, 85, 247, 0.08)'
                    : 'transparent'
                }
              >
                <YStack f={1}>
                  <Text
                    color={useGPU ? '#a855f7' : '#ffffff'}
                    fontSize={14}
                    fontWeight="600"
                  >
                    GPU Offload (Vulkan)
                  </Text>
                  <Text
                    color="rgba(255, 255, 255, 0.4)"
                    fontSize={11}
                    fontFamily="monospace"
                    mt="$1"
                  >
                    {useGPU
                      ? 'Offloading to GPU • Faster but uses more power'
                      : 'CPU only • Stable and power efficient'}
                  </Text>
                </YStack>
                <Switch
                  value={useGPU}
                  onValueChange={onToggleGPU}
                  trackColor={{
                    false: 'rgba(255, 255, 255, 0.15)',
                    true: 'rgba(168, 85, 247, 0.4)',
                  }}
                  thumbColor={useGPU ? '#a855f7' : '#ffffff'}
                />
              </XStack>
            </YStack>

            {/* Theme */}
            <YStack mb="$5">
              <Text
                color="rgba(255, 255, 255, 0.5)"
                fontSize={11}
                fontFamily="monospace"
                letterSpacing={1}
                mb="$3"
              >
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
                  <Text
                    color="rgba(255, 255, 255, 0.4)"
                    fontSize={11}
                    fontFamily="monospace"
                    mt="$1"
                  >
                    Optimized for AMOLED displays
                  </Text>
                </YStack>
                <Text color="#00ffff" fontSize={16}>
                  ✓
                </Text>
              </XStack>
            </YStack>

            {/* Data Management */}
            <YStack mb="$5">
              <Text
                color="rgba(255, 255, 255, 0.5)"
                fontSize={11}
                fontFamily="monospace"
                letterSpacing={1}
                mb="$3"
              >
                DATA MANAGEMENT
              </Text>

              {/* Delete Model */}
              {(modelStatus === 'ready' || modelStatus === 'not_downloaded') && (
                <TouchableOpacity
                  onPress={onDeleteModel}
                  activeOpacity={0.7}
                  style={{ marginBottom: 8 }}
                >
                  <XStack
                    ai="center"
                    p="$3"
                    borderRadius="$3"
                    borderWidth={0.5}
                    borderColor="rgba(239, 68, 68, 0.3)"
                    bg="rgba(239, 68, 68, 0.05)"
                  >
                    <Text color="#ef4444" fontSize={14}>
                      Delete model file (~2.5GB)
                    </Text>
                  </XStack>
                </TouchableOpacity>
              )}

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
            <YStack
              ai="center"
              pt="$3"
              borderTopWidth={0.5}
              borderTopColor="rgba(255, 255, 255, 0.08)"
            >
              <Text
                color="rgba(255, 255, 255, 0.25)"
                fontSize={11}
                fontFamily="monospace"
              >
                v0.1.0 • Powered by llama.cpp
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
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
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
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
});
