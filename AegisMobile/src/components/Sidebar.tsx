import React, { useEffect, useRef } from 'react';
import {
  Animated,
  Dimensions,
  TouchableOpacity,
  TouchableWithoutFeedback,
  StyleSheet,
  ScrollView,
  View,
} from 'react-native';
import { YStack, XStack, Text } from 'tamagui';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const SIDEBAR_WIDTH = SCREEN_WIDTH * 0.8;

interface Props {
  visible: boolean;
  onClose: () => void;
  onNewConversation: () => void;
  onOpenSettings: () => void;
}

export const Sidebar: React.FC<Props> = ({
  visible,
  onClose,
  onNewConversation,
  onOpenSettings,
}) => {
  const translateX = useRef(new Animated.Value(-SIDEBAR_WIDTH)).current;
  const backdropOpacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      Animated.parallel([
        Animated.timing(translateX, {
          toValue: 0,
          duration: 280,
          useNativeDriver: true,
        }),
        Animated.timing(backdropOpacity, {
          toValue: 1,
          duration: 280,
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      Animated.parallel([
        Animated.timing(translateX, {
          toValue: -SIDEBAR_WIDTH,
          duration: 220,
          useNativeDriver: true,
        }),
        Animated.timing(backdropOpacity, {
          toValue: 0,
          duration: 220,
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [visible]);

  if (!visible) return null;

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      {/* Backdrop */}
      <TouchableWithoutFeedback onPress={onClose}>
        <Animated.View
          style={[
            StyleSheet.absoluteFill,
            { backgroundColor: 'rgba(0, 0, 0, 0.6)', opacity: backdropOpacity },
          ]}
        />
      </TouchableWithoutFeedback>

      {/* Sidebar Panel */}
      <Animated.View
        style={[
          styles.panel,
          { transform: [{ translateX }] },
        ]}
      >
        {/* Header */}
        <XStack ai="center" jc="space-between" px="$4" pt="$6" pb="$4">
          <YStack>
            <XStack ai="center" gap="$2">
              <Text color="#00ffff" fontSize={12}>🛡️</Text>
              <Text color="#ffffff" fontSize={20} fontWeight="900" letterSpacing={2}>
                AEGIS
              </Text>
            </XStack>
            <Text color="rgba(255, 255, 255, 0.4)" fontSize={10} fontFamily="monospace" letterSpacing={1} mt="$1">
              INTELLIGENT COMPANION
            </Text>
          </YStack>
          <TouchableOpacity onPress={onClose} hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}>
            <Text color="rgba(255, 255, 255, 0.5)" fontSize={20}>✕</Text>
          </TouchableOpacity>
        </XStack>

        {/* New Conversation Button */}
        <YStack px="$4" pb="$3">
          <TouchableOpacity onPress={onNewConversation} activeOpacity={0.7}>
            <XStack
              ai="center"
              jc="center"
              py="$3"
              borderRadius="$4"
              borderWidth={1}
              borderColor="#00ffff"
              bg="rgba(0, 255, 255, 0.08)"
            >
              <Text color="#00ffff" fontSize={14} fontWeight="600">
                + New conversation
              </Text>
            </XStack>
          </TouchableOpacity>
        </YStack>

        {/* Search Bar */}
        <YStack px="$4" pb="$3">
          <XStack
            ai="center"
            px="$3"
            py="$2.5"
            borderRadius="$3"
            borderWidth={0.5}
            borderColor="rgba(255, 255, 255, 0.15)"
            bg="rgba(255, 255, 255, 0.03)"
          >
            <Text color="rgba(255, 255, 255, 0.3)" fontSize={13}>
              🔍  Search chats...
            </Text>
          </XStack>
        </YStack>

        {/* Conversation List */}
        <ScrollView style={styles.conversationList} contentContainerStyle={{ paddingHorizontal: 16 }}>
          <YStack ai="center" jc="center" py="$8">
            <Text color="rgba(255, 255, 255, 0.25)" fontSize={13}>
              No conversations yet
            </Text>
          </YStack>
        </ScrollView>

        {/* System Status */}
        <YStack borderTopWidth={0.5} borderTopColor="rgba(255, 255, 255, 0.08)" px="$4" pt="$3" pb="$2">
          <XStack ai="center" jc="space-between" mb="$2">
            <Text color="rgba(255, 255, 255, 0.5)" fontSize={11} fontFamily="monospace" letterSpacing={1}>
              AEGIS // SYS
            </Text>
            <XStack ai="center" gap="$1.5">
              <View style={styles.statusDot} />
              <Text color="#4ade80" fontSize={11} fontFamily="monospace">
                OK
              </Text>
            </XStack>
          </XStack>
          <XStack ai="center" gap="$2">
            <Text color="#00ffff" fontSize={11}>🛡️</Text>
            <Text color="rgba(255, 255, 255, 0.6)" fontSize={11} fontFamily="monospace">
              Gemma 4 E4B
            </Text>
          </XStack>
          <XStack ai="center" gap="$4" mt="$1">
            <Text color="rgba(255, 255, 255, 0.3)" fontSize={10} fontFamily="monospace">
              ↑ 0
            </Text>
            <Text color="rgba(0, 255, 255, 0.5)" fontSize={10} fontFamily="monospace">
              ↓ 0
            </Text>
          </XStack>
        </YStack>

        {/* Settings Button */}
        <TouchableOpacity onPress={onOpenSettings} activeOpacity={0.7}>
          <XStack
            ai="center"
            gap="$2"
            px="$4"
            py="$3"
            borderTopWidth={0.5}
            borderTopColor="rgba(255, 255, 255, 0.08)"
          >
            <Text color="rgba(255, 255, 255, 0.5)" fontSize={16}>⚙️</Text>
            <Text color="rgba(255, 255, 255, 0.6)" fontSize={14}>
              Settings
            </Text>
          </XStack>
        </TouchableOpacity>
      </Animated.View>
    </View>
  );
};

const styles = StyleSheet.create({
  panel: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    width: SIDEBAR_WIDTH,
    backgroundColor: 'rgba(5, 5, 15, 0.97)',
    borderRightWidth: 0.5,
    borderRightColor: 'rgba(0, 255, 255, 0.15)',
  },
  conversationList: {
    flex: 1,
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: '#4ade80',
  },
});
