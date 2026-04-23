import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';

interface Props {
  onInitialize: () => void;
}

export const LandingPage: React.FC<Props> = ({ onInitialize }) => {
  return (
    <View style={styles.container}>
      {/* Atmospheric Glow - Top Right */}
      <View style={styles.glowTopRight} />
      {/* Atmospheric Glow - Bottom Left */}
      <View style={styles.glowBottomLeft} />

      <View style={styles.content}>
        {/* Shield Icon */}
        <Text style={styles.shield}>🛡️</Text>

        <View style={styles.logoContainer}>
          <Text style={styles.title}>A E G I S</Text>
          <Text style={styles.subtitle}>BARE-METAL INTELLIGENCE</Text>
        </View>
        <Text style={styles.description}>
          Edge-optimized neural processing engine for Snapdragon Hexagon architecture.
        </Text>
      </View>
      <View style={styles.footer}>
        <TouchableOpacity 
          style={styles.initButton}
          onPress={onInitialize}
          activeOpacity={0.8}
        >
          <Text style={styles.initText}>INITIALIZE PROTOCOL</Text>
        </TouchableOpacity>
        <Text style={styles.versionText}>v0.0.1 • Powered by Aegis Engine</Text>
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
  shield: {
    fontSize: 56,
    marginBottom: 24,
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
  footer: {
    paddingBottom: 32,
    alignItems: 'center',
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
  initText: {
    color: '#000000',
    fontSize: 15,
    fontWeight: 'bold',
    letterSpacing: 2,
    fontFamily: 'monospace',
  },
  versionText: {
    color: 'rgba(255, 255, 255, 0.2)',
    fontSize: 10,
    fontFamily: 'monospace',
    marginTop: 16,
  },
});
