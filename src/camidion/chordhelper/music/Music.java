package camidion.chordhelper.music;

/**
 * 音楽理論ユーティリティ
 *
 * Circle-Of-Fifth based music theory functions
 *
 * @author Copyright (C) 2004-2014 ＠きよし - Akiyoshi Kamide
 */
public class Music {
	/**
	 * １オクターブの半音数
	 */
	public static final int SEMITONES_PER_OCTAVE = 12;
	/**
	 * MIDIノート番号と、
	 * メジャーキー基準の五度圏インデックス値との間の変換を行います。
	 *
	 * <p>双方向の変換に対応しています。
	 * 内部的には、元の値が奇数のときに6（半オクターブ）を足し、
	 * 偶数のときにそのまま返しているだけです。
	 * 値は0～11であるとは限りません。その範囲に補正したい場合は
	 *  {@link #mod12(int)} を併用します。
	 * </p>
	 *
	 * @param n 元の値
	 * @return 変換結果
	 */
	public static int reverseCo5(int n) {
		return (n & 1) == 0 ? n : n+6 ;
	}
	/**
	 * ノート番号からオクターブ成分を抜きます。
	 * <p>n % 12 と似ていますが、Java の % 演算子では、
	 * 左辺に負数を与えると答えも負数になってしまうため、n % 12 で計算しても
	 * 0～11 の範囲を外れてしまうことがあります。そこで、
	 * 負数の場合に12を足すことにより 0～11 の範囲に入るよう補正します。
	 * </p>
	 * @param n 元のノート番号
	 * @return オクターブ成分を抜いたノート番号（0～11）
	 */
	public static int mod12(int n) {
		int qn = n % SEMITONES_PER_OCTAVE;
		return qn < 0 ? qn + 12 : qn ;
	}
	/**
	 * 指定されたMIDIノート番号の音の周波数を返します。
	 * チューニングは A=440Hz とします。
	 *
	 * @param noteNumber MIDIノート番号
	 * @return 音の周波数[Hz]
	 */
	public static double noteNumberToFrequency(int noteNumber) {
		return 55 * Math.pow( 2, (double)(noteNumber - 33)/12 );
	}
	/**
	 * MIDIノート番号の示す音階が、
	 * 指定された調（五度圏インデックス値）におけるメジャースケールまたは
	 * ナチュラルマイナースケールの構成音に該当するか調べます。
	 *
	 * <p>調の五度圏インデックス値に０（ハ長調またはイ短調）を指定すると、
	 * 白鍵のときにtrue、黒鍵のときにfalseを返します。
	 * </p>
	 *
	 * @param noteNumber 調べたい音階のノート番号
	 * @param keyCo5 調の五度圏インデックス値
	 * @return スケール構成音のときtrue、スケールを外れている場合false
	 */
	public static boolean isOnScale(int noteNumber, int keyCo5) {
		return mod12(reverseCo5(noteNumber) - keyCo5 + 1) < 7 ;
	}
	/**
	 * 五度圏インデックス値で表された音階を、
	 * 指定された半音数だけ移調した結果を返します。
	 *
	 * <p>移調する半音数が０の場合、指定の五度圏インデックス値をそのまま返します。
	 * それ以外の場合、移調結果を -5 ～ 6 の範囲で返します。
	 * </p>
	 *
	 * @param co5 五度圏インデックス値
	 * @param chromaticOffset 移調する半音数
	 * @return 移調結果
	 */
	public static int transposeCo5(int co5, int chromaticOffset) {
		if( chromaticOffset == 0 ) return co5;
		int transposedCo5 = mod12( co5 + reverseCo5(chromaticOffset) );
		if( transposedCo5 > 6 ) transposedCo5 -= Music.SEMITONES_PER_OCTAVE;
		return transposedCo5;
	}
	/**
	 * 指定の五度圏インデックス値の真裏にあたる値を返します。
	 * @param co5 五度圏インデックス値
	 * @return 真裏の五度圏インデックス値
	 */
	public static int oppositeCo5(int co5) {
		return co5 > 0 ? co5 - 6 : co5 + 6;
	}
}

