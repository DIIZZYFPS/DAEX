import React, { useEffect, useRef } from 'react';
import { Animated } from 'react-native';
import Svg, { G, Circle } from 'react-native-svg';

const AnimatedG = Animated.createAnimatedComponent(G);

interface Props {
  size?: number;
}

export const DaexLoader: React.FC<Props> = ({ size = 40 }) => {
  const cx = size / 2;
  const cy = size / 2;

  // Ring rotations
  const ring1Rot = useRef(new Animated.Value(0)).current;
  const ring2Rot = useRef(new Animated.Value(0)).current;
  // Core pulse
  const coreOpacity = useRef(new Animated.Value(0.7)).current;

  useEffect(() => {
    Animated.loop(
      Animated.timing(ring1Rot, {
        toValue: 1,
        duration: 2000,
        useNativeDriver: true,
      }),
    ).start();

    Animated.loop(
      Animated.timing(ring2Rot, {
        toValue: -1,
        duration: 3000,
        useNativeDriver: true,
      }),
    ).start();

    Animated.loop(
      Animated.sequence([
        Animated.timing(coreOpacity, {
          toValue: 1,
          duration: 500,
          useNativeDriver: true,
        }),
        Animated.timing(coreOpacity, {
          toValue: 0.7,
          duration: 500,
          useNativeDriver: true,
        }),
      ]),
    ).start();
  }, []);

  const ring1Rotate = ring1Rot.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  });

  const ring2Rotate = ring2Rot.interpolate({
    inputRange: [-1, 0],
    outputRange: ['-360deg', '0deg'],
  });

  // Ring dimensions relative to size
  const coreR  = size * 0.08;
  const ring1R = size * 0.18;
  const ring2R = size * 0.26;
  const strokeW1 = size * 0.02;
  const strokeW2 = size * 0.015;

  // Dash arrays scaled to ring circumference
  const dash1 = `${ring1R * 0.6} ${ring1R * 2.4} ${ring1R * 1.2} ${ring1R * 3}`;
  const dash2 = `${ring2R * 1.2} ${ring2R * 2.4} ${ring2R * 0.6} ${ring2R * 1.5}`;

  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      {/* Core star */}
      <AnimatedG style={{ opacity: coreOpacity }}>
        <Circle cx={cx} cy={cy} r={coreR} fill="#00ffff" />
      </AnimatedG>

      {/* Ring 1 — clockwise */}
      <AnimatedG
        style={{
          transform: [
            { translateX: cx },
            { translateY: cy },
            { rotate: ring1Rotate },
            { translateX: -cx },
            { translateY: -cy },
          ],
        }}
      >
        <Circle
          cx={cx}
          cy={cy}
          r={ring1R}
          fill="none"
          stroke="#00ffff"
          strokeWidth={strokeW1}
          strokeDasharray={dash1}
        />
      </AnimatedG>

      {/* Ring 2 — counter-clockwise */}
      <AnimatedG
        style={{
          transform: [
            { translateX: cx },
            { translateY: cy },
            { rotate: ring2Rotate },
            { translateX: -cx },
            { translateY: -cy },
          ],
        }}
      >
        <Circle
          cx={cx}
          cy={cy}
          r={ring2R}
          fill="none"
          stroke="rgba(0,255,255,0.4)"
          strokeWidth={strokeW2}
          strokeDasharray={dash2}
        />
      </AnimatedG>
    </Svg>
  );
};
