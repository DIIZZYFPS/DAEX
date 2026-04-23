import React, { useState, useRef, useEffect } from 'react';
import {
  FlatList,
  Platform,
  StyleSheet,
  TouchableOpacity,
  Keyboard,
  View,
  ActivityIndicator,
} from 'react-native';
import { BlurView } from '@react-native-community/blur';
import { YStack, XStack, Text, Input, Button } from 'tamagui';
import { useAegisInference } from '../hooks/useAegisInference';
import { MessageLine } from './MessageLine';
import { Sidebar } from './Sidebar';
import { SettingsModal } from './SettingsModal';
import { SuggestedPrompts } from './SuggestedPrompts';
import { modelManager } from '../services/modelManager';

interface Props {
  onBack: () => void;
}

export const MobileExecution: React.FC<Props> = ({ onBack }) => {
  const [inputText, setInputText] = useState('');
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  const [sidebarVisible, setSidebarVisible] = useState(false);
  const [settingsVisible, setSettingsVisible] = useState(false);
  const {
    messages,
    isGenerating,
    hardwareState,
    submitPrompt,
    modelStatus,
    downloadProgress,
    tokenSpeed,
    errorMessage,
    useGPU,
    loadModel,
    unloadModel,
    downloadModel,
    toggleGPU,
    clearMessages,
  } = useAegisInference();
  const flatListRef = useRef<FlatList>(null);

  useEffect(() => {
    const showSubscription = Keyboard.addListener(
      Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow',
      (e) => setKeyboardHeight(e.endCoordinates.height),
    );
    const hideSubscription = Keyboard.addListener(
      Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide',
      () => setKeyboardHeight(0),
    );

    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, []);

  const handleSend = (text?: string) => {
    const toSend = text || inputText;
    if (toSend.trim() && !isGenerating) {
      submitPrompt(toSend);
      setInputText('');
    }
  };

  const handleSelectPrompt = (prompt: string) => {
    handleSend(prompt);
  };

  const handleNewConversation = () => {
    setSidebarVisible(false);
    clearMessages();
  };

  const handleOpenSettings = () => {
    setSidebarVisible(false);
    setSettingsVisible(true);
  };

  const handleDeleteModel = async () => {
    await unloadModel();
    await modelManager.deleteModel();
    setSettingsVisible(false);
    onBack();
  };

  const isModelReady = modelStatus === 'ready';

  const getStatusBadge = () => {
    if (isGenerating && tokenSpeed > 0) {
      return `${tokenSpeed} tok/s`;
    }
    if (isGenerating) {
      return 'Generating...';
    }
    if (modelStatus === 'loading') {
      return 'Loading...';
    }
    if (modelStatus === 'downloading') {
      return `${downloadProgress}%`;
    }
    return hardwareState;
  };

  const getStatusColor = () => {
    if (modelStatus === 'error') return '#ef4444';
    if (modelStatus === 'downloading' || modelStatus === 'loading')
      return '#f59e0b';
    if (isGenerating) return '#a855f7';
    if (isModelReady) return '#4ade80';
    return '#f59e0b';
  };

  const [isAtBottom, setIsAtBottom] = useState(true);

  const handleScroll = (event: any) => {
    const { layoutMeasurement, contentOffset, contentSize } = event.nativeEvent;
    // Footer is 120 + keyboard height, so we check if within that range + a buffer
    const isCloseToBottom =
      layoutMeasurement.height + contentOffset.y >= contentSize.height - 180;
    setIsAtBottom(isCloseToBottom);
  };

  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <YStack f={1} bg="#000000">
        <YStack flex={1}>
          {/* Header */}
          <XStack
            ai="center"
            jc="space-between"
            paddingHorizontal="$4"
            paddingTop="$6"
            paddingBottom="$3"
            borderBottomWidth={0.5}
            borderBottomColor="rgba(0, 255, 255, 0.15)"
          >
            <XStack ai="center">
              <TouchableOpacity
                onPress={() => setSidebarVisible(true)}
                style={{ marginRight: 16 }}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Text color="#00ffff" fontSize={22}>
                  ☰
                </Text>
              </TouchableOpacity>
              <XStack ai="center" gap="$1.5">
                <Text color="#00ffff" fontSize={14}>
                  🛡️
                </Text>
                <Text
                  color="#ffffff"
                  fontSize={16}
                  fontWeight="bold"
                  letterSpacing={2}
                >
                  AEGIS
                </Text>
              </XStack>
            </XStack>
            <XStack ai="center" gap="$3">
              <XStack
                ai="center"
                bg="rgba(0, 255, 255, 0.08)"
                px="$2.5"
                py="$1.5"
                borderRadius="$3"
                borderWidth={0.5}
                borderColor="rgba(0, 255, 255, 0.2)"
              >
                <View
                  style={{
                    width: 6,
                    height: 6,
                    borderRadius: 3,
                    backgroundColor: getStatusColor(),
                    marginRight: 6,
                  }}
                />
                <Text
                  color="#e2e8f0"
                  fontSize={10}
                  fontFamily="monospace"
                >
                  {getStatusBadge()}
                </Text>
              </XStack>
              <TouchableOpacity
                onPress={handleNewConversation}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Text color="#00ffff" fontSize={22}>
                  +
                </Text>
              </TouchableOpacity>
            </XStack>
          </XStack>

          {/* Model Status Banner */}
          {!isModelReady && (
            <YStack
              px="$4"
              py="$3"
              bg="rgba(245, 158, 11, 0.08)"
              borderBottomWidth={0.5}
              borderBottomColor="rgba(245, 158, 11, 0.2)"
            >
              {modelStatus === 'downloading' ? (
                <YStack gap="$2">
                  <XStack ai="center" gap="$2">
                    <ActivityIndicator size="small" color="#f59e0b" />
                    <Text
                      color="#f59e0b"
                      fontSize={12}
                      fontFamily="monospace"
                    >
                      Downloading model... {downloadProgress}%
                    </Text>
                  </XStack>
                  <View style={styles.progressBarTrack}>
                    <View
                      style={[
                        styles.progressBarFill,
                        { width: `${downloadProgress}%` },
                      ]}
                    />
                  </View>
                </YStack>
              ) : modelStatus === 'loading' ? (
                <XStack ai="center" gap="$2">
                  <ActivityIndicator size="small" color="#f59e0b" />
                  <Text
                    color="#f59e0b"
                    fontSize={12}
                    fontFamily="monospace"
                  >
                    Loading model into memory...
                  </Text>
                </XStack>
              ) : modelStatus === 'error' ? (
                <XStack ai="center" jc="space-between">
                  <Text
                    color="#ef4444"
                    fontSize={12}
                    fontFamily="monospace"
                    f={1}
                  >
                    {errorMessage || 'Model error'}
                  </Text>
                  <TouchableOpacity onPress={loadModel}>
                    <Text
                      color="#00ffff"
                      fontSize={12}
                      fontFamily="monospace"
                    >
                      RETRY
                    </Text>
                  </TouchableOpacity>
                </XStack>
              ) : (
                <TouchableOpacity onPress={loadModel}>
                  <XStack ai="center" jc="center" gap="$2">
                    <Text
                      color="#f59e0b"
                      fontSize={12}
                      fontFamily="monospace"
                    >
                      Model not loaded — tap to initialize
                    </Text>
                  </XStack>
                </TouchableOpacity>
              )}
            </YStack>
          )}

          {/* Chat Area or Welcome */}
          {messages.length === 0 ? (
            <SuggestedPrompts onSelectPrompt={handleSelectPrompt} />
          ) : (
            <YStack f={1}>
              <FlatList
                ref={flatListRef}
                data={messages}
                keyExtractor={(item) => item.id}
                renderItem={({ item, index }) => {
                  const isLastModel =
                    item.role === 'model' &&
                    !messages.slice(index + 1).some((m) => m.role === 'model');
                  return (
                    <MessageLine
                      message={item}
                      isLastModel={isLastModel}
                      isGenerating={isGenerating}
                      tokenSpeed={tokenSpeed}
                    />
                  );
                }}
                ListFooterComponent={<View style={{ height: 120 + keyboardHeight }} />}
                contentContainerStyle={{
                  paddingVertical: 16,
                  flexGrow: 1,
                }}
                removeClippedSubviews={false}
                onScroll={handleScroll}
                scrollEventThrottle={16}
                onContentSizeChange={() => {
                  if (isAtBottom || isGenerating) {
                    // Turn off animation during streaming to eliminate yanking
                    flatListRef.current?.scrollToEnd({ animated: !isGenerating });
                  }
                }}
                onLayout={() => {
                  if (isAtBottom) {
                    flatListRef.current?.scrollToEnd({ animated: true });
                  }
                }}
              />
            </YStack>
          )}

          {/* Frosted Glass Input Area */}
          <YStack
            position="absolute"
            bottom={keyboardHeight}
            left={0}
            right={0}
            pb={Platform.OS === 'ios' ? '$6' : '$4'}
            overflow="hidden"
          >
            <BlurView
              style={StyleSheet.absoluteFill}
              blurType="dark"
              blurAmount={15}
              reducedTransparencyFallbackColor="#000000"
            />

            <XStack ai="center" mx="$4" mt="$3" p="$1.5" bg="transparent">
              <Input
                f={1}
                borderWidth={0}
                bg="transparent"
                color="#ffffff"
                fontSize={16}
                value={inputText}
                onChangeText={setInputText}
                placeholder={
                  isModelReady
                    ? 'Send protocol to Aegis...'
                    : 'Model not loaded...'
                }
                placeholderTextColor={"rgba(255, 255, 255, 0.4)" as any}
                onSubmitEditing={() => handleSend()}
                disabled={isGenerating || !isModelReady}
              />
              <Button
                size="$3"
                circular
                bg={
                  isGenerating || !isModelReady
                    ? 'rgba(0, 255, 255, 0.2)'
                    : '#00ffff'
                }
                onPress={() => handleSend()}
                disabled={isGenerating || !isModelReady}
                ml="$2"
              >
                <YStack
                  w={12}
                  h={12}
                  borderRadius={6}
                  bg={
                    isGenerating || !isModelReady
                      ? 'rgba(0, 255, 255, 0.5)'
                      : '#000000'
                  }
                />
              </Button>
            </XStack>
          </YStack>
        </YStack>
      </YStack>

      {/* Overlays */}
      <Sidebar
        visible={sidebarVisible}
        onClose={() => setSidebarVisible(false)}
        onNewConversation={handleNewConversation}
        onOpenSettings={handleOpenSettings}
      />
      <SettingsModal
        visible={settingsVisible}
        onClose={() => setSettingsVisible(false)}
        modelStatus={modelStatus}
        useGPU={useGPU}
        onToggleGPU={toggleGPU}
        onDownloadModel={downloadModel}
        onDeleteModel={handleDeleteModel}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  progressBarTrack: {
    width: '100%',
    height: 3,
    backgroundColor: 'rgba(245, 158, 11, 0.15)',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressBarFill: {
    height: '100%',
    backgroundColor: '#f59e0b',
    borderRadius: 2,
  },
});
