import React, { useEffect, useState } from 'react';
import {
  TouchableOpacity,
  StyleSheet,
  FlatList,
  View,
  ActivityIndicator,
  Modal,
  TouchableWithoutFeedback,
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
import { Model, MODEL_BANK } from '../services/modelBank';
import { modelManager } from '../services/modelManager';

const SCREEN_HEIGHT = Dimensions.get('window').height;
const SHEET_HEIGHT = SCREEN_HEIGHT * 0.72;

// Physics-tuned spring config — matches iOS sheet feel
const OPEN_SPRING = { damping: 26, stiffness: 300, mass: 0.8 };
const CLOSE_TIMING = { duration: 260, easing: Easing.in(Easing.cubic) };

interface ModelSupportStatus {
  hasEnoughRAM: boolean;
  hasEnoughStorage: boolean;
  checked: boolean;
}

interface Props {
  visible: boolean;
  selectedModel: Model;
  onSelect: (model: Model) => void;
  onClose: () => void;
}

export const ModelSelectorModal: React.FC<Props> = ({
  visible,
  selectedModel,
  onSelect,
  onClose,
}) => {
  const [supportMap, setSupportMap] = useState<Record<string, ModelSupportStatus>>({});

  // Shared values — live on UI thread
  const translateY = useSharedValue(SHEET_HEIGHT);
  const backdropOpacity = useSharedValue(0);
  const dragOffset = useSharedValue(0);

  // Hardware capability checks
  useEffect(() => {
    if (!visible) return;

    const checks = MODEL_BANK.map(async (model) => {
      try {
        const spec = await modelManager.checkSpecSupport(model);
        return {
          id: model.id,
          status: {
            hasEnoughRAM: spec.hasEnoughRAM,
            hasEnoughStorage: spec.hasEnoughStorage,
            checked: true,
          },
        };
      } catch {
        return {
          id: model.id,
          status: { hasEnoughRAM: false, hasEnoughStorage: false, checked: true },
        };
      }
    });

    Promise.all(checks).then((results) => {
      const map: Record<string, ModelSupportStatus> = {};
      results.forEach(({ id, status }) => {
        map[id] = status;
      });
      setSupportMap(map);
    });
  }, [visible]);

  // Animate in/out
  useEffect(() => {
    if (visible) {
      translateY.value = withSpring(0, OPEN_SPRING);
      backdropOpacity.value = withTiming(1, { duration: 280 });
    }
  }, [visible]);

  const closeSheet = () => {
    'worklet';
    translateY.value = withTiming(SHEET_HEIGHT, CLOSE_TIMING, (finished) => {
      if (finished) runOnJS(onClose)();
    });
    backdropOpacity.value = withTiming(0, { duration: 220 });
  };

  // Reset animation values after close so the next open starts fresh
  const handleClose = () => {
    closeSheet();
  };

  // Drag-to-dismiss gesture
  const dragGesture = Gesture.Pan()
    .onUpdate((e) => {
      // Only allow downward drag
      dragOffset.value = Math.max(0, e.translationY);
      translateY.value = dragOffset.value;
      // Fade backdrop as user drags down
      backdropOpacity.value = Math.max(0, 1 - dragOffset.value / (SHEET_HEIGHT * 0.5));
    })
    .onEnd((e) => {
      // Dismiss if dragged more than 25% down or thrown fast
      if (dragOffset.value > SHEET_HEIGHT * 0.25 || e.velocityY > 800) {
        translateY.value = withTiming(SHEET_HEIGHT, CLOSE_TIMING, (finished) => {
          if (finished) runOnJS(onClose)();
        });
        backdropOpacity.value = withTiming(0, { duration: 200 });
      } else {
        // Snap back
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

  const formatBytes = (bytes: number) => {
    const gb = bytes / (1024 * 1024 * 1024);
    return `${gb.toFixed(1)} GB`;
  };

  const renderModel = ({ item }: { item: Model }) => {
    const support = supportMap[item.id];
    const isSelected = item.id === selectedModel.id;
    const isSupported = !support || (support.hasEnoughRAM && support.hasEnoughStorage);
    const isChecked = support?.checked ?? false;

    return (
      <TouchableOpacity
        onPress={() => {
          if (isSupported) {
            onSelect(item);
            handleClose();
          }
        }}
        activeOpacity={isSupported ? 0.7 : 1}
        style={{ marginBottom: 10 }}
      >
        <YStack
          p="$3"
          borderRadius="$4"
          borderWidth={isSelected ? 1 : 0.5}
          borderColor={
            isSelected
              ? '#00ffff'
              : isChecked && !isSupported
              ? 'rgba(239, 68, 68, 0.3)'
              : 'rgba(255, 255, 255, 0.12)'
          }
          bg={
            isSelected
              ? 'rgba(0, 255, 255, 0.07)'
              : isChecked && !isSupported
              ? 'rgba(239, 68, 68, 0.04)'
              : 'rgba(255, 255, 255, 0.03)'
          }
          opacity={isChecked && !isSupported ? 0.65 : 1}
        >
          <XStack ai="center" jc="space-between" mb="$1.5">
            <Text
              color={isSelected ? '#00ffff' : isChecked && !isSupported ? '#ef4444' : '#ffffff'}
              fontSize={14}
              fontWeight="600"
              f={1}
              mr="$2"
            >
              {item.name}
            </Text>

            {!isChecked ? (
              <ActivityIndicator size="small" color="rgba(255,255,255,0.3)" />
            ) : isSupported ? (
              <XStack
                ai="center"
                px="$2"
                py="$1"
                borderRadius="$2"
                bg="rgba(74, 222, 128, 0.12)"
                borderWidth={0.5}
                borderColor="rgba(74, 222, 128, 0.3)"
              >
                <Text color="#4ade80" fontSize={9} fontFamily="monospace">
                  ✓ SUPPORTED
                </Text>
              </XStack>
            ) : (
              <XStack
                ai="center"
                px="$2"
                py="$1"
                borderRadius="$2"
                bg="rgba(239, 68, 68, 0.12)"
                borderWidth={0.5}
                borderColor="rgba(239, 68, 68, 0.3)"
              >
                <Text color="#ef4444" fontSize={9} fontFamily="monospace">
                  ✗ INSUFFICIENT RAM
                </Text>
              </XStack>
            )}
          </XStack>

          <XStack gap="$3">
            <Text color="rgba(255,255,255,0.4)" fontSize={11} fontFamily="monospace">
              {formatBytes(item.size)}
            </Text>
            <Text color="rgba(255,255,255,0.2)" fontSize={11} fontFamily="monospace">•</Text>
            <Text color="rgba(255,255,255,0.4)" fontSize={11} fontFamily="monospace">
              Needs {formatBytes(item.requiredRAM)} RAM
            </Text>
            {isSelected && (
              <>
                <Text color="rgba(255,255,255,0.2)" fontSize={11} fontFamily="monospace">•</Text>
                <Text color="#00ffff" fontSize={11} fontFamily="monospace">ACTIVE</Text>
              </>
            )}
          </XStack>

          <Text
            color="rgba(255,255,255,0.3)"
            fontSize={11}
            fontFamily="monospace"
            mt="$1.5"
            numberOfLines={2}
          >
            {item.description}
          </Text>
        </YStack>
      </TouchableOpacity>
    );
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      statusBarTranslucent
      onRequestClose={handleClose}
    >
      {/* Animated backdrop */}
      <Animated.View style={[styles.backdrop, backdropStyle]}>
        <TouchableWithoutFeedback onPress={handleClose}>
          <View style={StyleSheet.absoluteFill} />
        </TouchableWithoutFeedback>
      </Animated.View>

      {/* Drag-to-dismiss + animated sheet */}
      <GestureDetector gesture={dragGesture}>
        <Animated.View style={[styles.sheet, sheetStyle]}>
          {/* Drag handle */}
          <View style={styles.handle} />

          {/* Header */}
          <XStack ai="center" jc="space-between" mb="$4">
            <YStack>
              <Text color="#ffffff" fontSize={18} fontWeight="bold" letterSpacing={1}>
                Select Engine
              </Text>
              <Text color="rgba(255,255,255,0.4)" fontSize={11} fontFamily="monospace" mt="$0.5">
                HARDWARE CAPABILITY CHECK
              </Text>
            </YStack>
            <TouchableOpacity
              onPress={handleClose}
              hitSlop={{ top: 16, bottom: 16, left: 16, right: 16 }}
            >
              <Text color="rgba(255, 255, 255, 0.5)" fontSize={20}>✕</Text>
            </TouchableOpacity>
          </XStack>

          <FlatList
            data={MODEL_BANK}
            keyExtractor={(item) => item.id}
            renderItem={renderModel}
            showsVerticalScrollIndicator={false}
            ListFooterComponent={<View style={{ height: 32 }} />}
          />
        </Animated.View>
      </GestureDetector>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
  },
  sheet: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: SHEET_HEIGHT,
    backgroundColor: '#080812',
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    borderTopWidth: 0.5,
    borderLeftWidth: 0.5,
    borderRightWidth: 0.5,
    borderColor: 'rgba(0, 255, 255, 0.18)',
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
});
