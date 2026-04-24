import React from 'react';
import {
  TouchableOpacity,
  TouchableWithoutFeedback,
  StyleSheet,
  ScrollView,
  View,
  Switch,
  Modal,
  Dimensions,
} from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
  runOnJS,
  Easing,
} from 'react-native-reanimated';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { YStack, XStack, Text } from 'tamagui';
import { ModelStatus } from '../hooks/useDaexInference';
import { Model } from '../services/modelBank';

const SCREEN_HEIGHT = Dimensions.get('window').height;
const OPEN_SPRING = { damping: 26, stiffness: 300, mass: 0.8 };
const CLOSE_TIMING = { duration: 260, easing: Easing.in(Easing.cubic) };

interface Props {
  visible: boolean;
  onClose: () => void;
  modelStatus: ModelStatus;
  selectedModel: Model;
  useGPU: boolean;
  onToggleGPU: (enabled: boolean) => void;
  onDownloadModel: () => void;
  onDeleteModel: () => void;
}


export const SettingsModal: React.FC<Props> = ({
  visible,
  onClose,
  modelStatus,
  selectedModel,
  useGPU,
  onToggleGPU,
  onDownloadModel,
  onDeleteModel,
}) => {
  const translateY = useSharedValue(SCREEN_HEIGHT);
  const backdropOpacity = useSharedValue(0);
  const dragOffset = useSharedValue(0);

  React.useEffect(() => {
    if (visible) {
      translateY.value = withSpring(0, OPEN_SPRING);
      backdropOpacity.value = withTiming(1, { duration: 280 });
    } else {
      translateY.value = SCREEN_HEIGHT;
      backdropOpacity.value = 0;
    }
  }, [visible]);

  const closeSheet = () => {
    'worklet';
    translateY.value = withTiming(SCREEN_HEIGHT, CLOSE_TIMING, (finished) => {
      if (finished) runOnJS(onClose)();
    });
    backdropOpacity.value = withTiming(0, { duration: 220 });
  };

  const handleClose = () => { closeSheet(); };

  const dragGesture = Gesture.Pan()
    .onUpdate((e) => {
      dragOffset.value = Math.max(0, e.translationY);
      translateY.value = dragOffset.value;
      backdropOpacity.value = Math.max(0, 1 - dragOffset.value / (SCREEN_HEIGHT * 0.5));
    })
    .onEnd((e) => {
      if (dragOffset.value > SCREEN_HEIGHT * 0.3 || e.velocityY > 800) {
        translateY.value = withTiming(SCREEN_HEIGHT, CLOSE_TIMING, (finished) => {
          if (finished) runOnJS(onClose)();
        });
        backdropOpacity.value = withTiming(0, { duration: 200 });
      } else {
        translateY.value = withSpring(0, OPEN_SPRING);
        backdropOpacity.value = withTiming(1, { duration: 200 });
      }
      dragOffset.value = 0;
    });

  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));

  const backdropStyle = useAnimatedStyle(() => ({
    opacity: backdropOpacity.value,
  }));

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
    <Modal
      visible={visible}
      transparent
      animationType="none"
      statusBarTranslucent
      onRequestClose={handleClose}
    >
      <Animated.View style={[styles.backdrop, backdropStyle]}>
        <TouchableWithoutFeedback onPress={handleClose}>
          <View style={StyleSheet.absoluteFill} />
        </TouchableWithoutFeedback>
      </Animated.View>

      <GestureDetector gesture={dragGesture}>
        <Animated.View style={[styles.sheetContainer, sheetStyle]}>
          <View style={styles.handle} />
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
                      {selectedModel.name}
                    </Text>
                    <Text
                      color="rgba(255, 255, 255, 0.4)"
                      fontSize={11}
                      fontFamily="monospace"
                      mt="$1"
                    >
                      {(selectedModel.size / 1e9).toFixed(1)}GB • {(selectedModel.requiredRAM / 1e9).toFixed(0)}GB RAM req.
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
                      Delete model file (~{(selectedModel.size / 1e9).toFixed(1)}GB)
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
        </Animated.View>
      </GestureDetector>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
  },
  sheetContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    maxHeight: '88%',
    backgroundColor: '#080812',
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    borderTopWidth: 0.5,
    borderLeftWidth: 0.5,
    borderRightWidth: 0.5,
    borderColor: 'rgba(255, 255, 255, 0.1)',
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 40,
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.18)',
    alignSelf: 'center',
    marginBottom: 20,
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
});
