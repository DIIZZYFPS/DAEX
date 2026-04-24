import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
import { ModelStatus } from '../hooks/useDaexInference';
import { Model } from '../services/modelBank';
import { DaexLogo } from './DaexLogo';
import { DaexLoader } from './DaexLoader';

interface Props {
  onInitialize: () => void;
  onChangeModel: () => void;
  modelStatus: ModelStatus;
  downloadProgress: number;
  selectedModel: Model;
}

export const LandingPage: React.FC<Props> = ({
  onInitialize,
  onChangeModel,
  modelStatus,
  downloadProgress,
  selectedModel,
}) => {
  const isWorking =
    modelStatus === 'downloading' || modelStatus === 'loading';

  const getButtonText = () => {
    switch (modelStatus) {
      case 'downloading':
        return `DOWNLOADING MODEL ${downloadProgress}%`;
      case 'loading':
        return 'LOADING MODEL...';
      case 'error':
        return 'RETRY INITIALIZATION';
      default:
        return 'INITIALIZE PROTOCOL';
    }
  };

  const getSubtitle = () => {
    switch (modelStatus) {
      case 'downloading':
        return `Pulling ${selectedModel.name} from HuggingFace CDN...`;
      case 'loading':
        return 'Loading model into device memory...';
      case 'error':
        return 'Initialization failed. Tap to retry.';
      default:
        return 'Edge-optimized neural processing engine for on-device inference.';
    }
  };

  const formatBytes = (bytes: number) => {
    const gb = bytes / (1024 * 1024 * 1024);
    return `${gb.toFixed(1)}GB`;
  };

  return (
    <View style={styles.container}>
      {/* Atmospheric Glow - Top Right */}
      <View style={styles.glowTopRight} />
      {/* Atmospheric Glow - Bottom Left */}
      <View style={styles.glowBottomLeft} />

      <View style={styles.content}>
        {/* Shield Icon */}
        <DaexLogo size={80} ambient />

        <View style={styles.logoContainer}>
          <Text style={styles.title}>D A E X</Text>
          <Text style={styles.subtitle}>DAEDALUS EXECUTION ENGINE</Text>
        </View>
        <Text style={styles.description}>{getSubtitle()}</Text>

        {/* Download Progress Bar */}
        {modelStatus === 'downloading' && (
          <View style={styles.progressBarContainer}>
            <View style={styles.progressBarTrack}>
              <View
                style={[
                  styles.progressBarFill,
                  { width: `${downloadProgress}%` },
                ]}
              />
            </View>
            <Text style={styles.progressText}>
              {downloadProgress}% complete
            </Text>
          </View>
        )}

        {/* Loading Spinner */}
        {modelStatus === 'loading' && (
          <View style={styles.loadingContainer}>
            <DaexLoader size={60} />
            <Text style={styles.loadingText}>
              Initializing execution engine...
            </Text>
          </View>
        )}
      </View>

      <View style={styles.footer}>
        <TouchableOpacity
          style={[styles.initButton, isWorking && styles.initButtonDisabled]}
          onPress={onInitialize}
          activeOpacity={0.8}
          disabled={isWorking}
        >
          {isWorking ? (
            <View style={styles.buttonContent}>
              <ActivityIndicator
                size="small"
                color={isWorking ? '#00ffff' : '#000000'}
                style={{ marginRight: 8 }}
              />
              <Text
                style={[styles.initText, isWorking && styles.initTextWorking]}
              >
                {getButtonText()}
              </Text>
            </View>
          ) : (
            <Text style={styles.initText}>{getButtonText()}</Text>
          )}
        </TouchableOpacity>

        {/* Tappable model selector pill */}
        <TouchableOpacity
          onPress={onChangeModel}
          activeOpacity={0.7}
          disabled={isWorking}
          style={styles.selectorPill}
        >
          <View style={styles.selectorDot} />
          <Text style={styles.selectorLabel}>
            {selectedModel.name}
          </Text>
          <Text style={styles.selectorMeta}>
            {formatBytes(selectedModel.size)} · {formatBytes(selectedModel.requiredRAM)} RAM ›
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000000',
    justifyContent: 'space-between',
    padding: 24,
  },
  glowTopRight: {
    position: 'absolute',
    top: -80,
    right: -80,
    width: 260,
    height: 260,
    borderRadius: 130,
    backgroundColor: 'rgba(0, 255, 255, 0.04)',
  },
  glowBottomLeft: {
    position: 'absolute',
    bottom: -60,
    left: -60,
    width: 200,
    height: 200,
    borderRadius: 100,
    backgroundColor: 'rgba(0, 100, 255, 0.03)',
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  logoContainer: {
    alignItems: 'center',
    marginBottom: 24,
    padding: 32,
    borderRadius: 100,
    borderWidth: 0.5,
    borderColor: 'rgba(0, 255, 255, 0.2)',
    backgroundColor: 'rgba(0, 255, 255, 0.04)',
  },
  title: {
    color: '#00ffff',
    fontSize: 42,
    fontWeight: '900',
    letterSpacing: 8,
  },
  subtitle: {
    color: '#e2e8f0',
    fontSize: 11,
    fontFamily: 'monospace',
    marginTop: 8,
    letterSpacing: 3,
  },
  description: {
    color: 'rgba(255, 255, 255, 0.5)',
    fontSize: 14,
    textAlign: 'center',
    fontFamily: 'monospace',
    lineHeight: 22,
    paddingHorizontal: 20,
  },
  progressBarContainer: {
    width: '80%',
    marginTop: 24,
    alignItems: 'center',
  },
  progressBarTrack: {
    width: '100%',
    height: 4,
    backgroundColor: 'rgba(0, 255, 255, 0.15)',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressBarFill: {
    height: '100%',
    backgroundColor: '#00ffff',
    borderRadius: 2,
  },
  progressText: {
    color: 'rgba(0, 255, 255, 0.6)',
    fontSize: 11,
    fontFamily: 'monospace',
    marginTop: 8,
  },
  loadingContainer: {
    marginTop: 24,
    alignItems: 'center',
    gap: 12,
  },
  loadingText: {
    color: 'rgba(0, 255, 255, 0.6)',
    fontSize: 12,
    fontFamily: 'monospace',
  },
  footer: {
    paddingBottom: 32,
    alignItems: 'center',
    gap: 0,
  },
  initButton: {
    width: '100%',
    backgroundColor: '#00ffff',
    paddingVertical: 16,
    borderRadius: 30,
    alignItems: 'center',
    shadowColor: '#00ffff',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.4,
    shadowRadius: 20,
    elevation: 12,
  },
  initButtonDisabled: {
    backgroundColor: 'rgba(0, 255, 255, 0.15)',
    borderWidth: 1,
    borderColor: 'rgba(0, 255, 255, 0.3)',
    elevation: 0,
    shadowOpacity: 0,
  },
  buttonContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  initText: {
    color: '#000000',
    fontSize: 15,
    fontWeight: 'bold',
    letterSpacing: 2,
    fontFamily: 'monospace',
  },
  initTextWorking: {
    color: '#00ffff',
    fontSize: 12,
    letterSpacing: 1,
  },
  selectorPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 20,
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderWidth: 0.5,
    borderColor: 'rgba(0, 255, 255, 0.2)',
    backgroundColor: 'rgba(0, 255, 255, 0.05)',
  },
  selectorDot: {
    width: 5,
    height: 5,
    borderRadius: 3,
    backgroundColor: '#00ffff',
  },
  selectorLabel: {
    color: 'rgba(0, 255, 255, 0.8)',
    fontSize: 11,
    fontFamily: 'monospace',
    fontWeight: '600',
  },
  selectorMeta: {
    color: 'rgba(255, 255, 255, 0.35)',
    fontSize: 11,
    fontFamily: 'monospace',
  },
});

