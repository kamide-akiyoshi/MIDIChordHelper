package camidion.chordhelper.music;

// for ComboBoxModel implementation


/**
 * 音楽理論・自動作曲アルゴリズムの Java 実装
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
	 * メジャーキー基準の五度圏インデックス値との間で変換を行います。
	 * <p>このメソッドで両方向の変換に対応しています。
	 * 内部的には、元の値が奇数のときに6（１オクターブ12半音の半分）を足し、
	 * 偶数のときにそのまま返しているだけです。
	 * 値は0～11であるとは限りません。その範囲に補正したい場合は
	 *  {@link #mod12(int)} を併用します。
	 * </p>
	 *
	 * @param n 元の値
	 * @return 変換結果
	 */
	public static int reverseCo5(int n) { // Swap C D E <-> Gb Ab Bb
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
	 * 指定のMIDIノート番号に対応する、A=440Hzとした場合の音の周波数を返します。
	 * @param noteNo MIDIノート番号
	 * @return A=440Hzとした場合の音の周波数[Hz]
	 */
	public static double noteNoToFrequency(int noteNo) {
		// Returns frequency when A=440Hz
		return 55 * Math.pow( 2, (double)(noteNo - 33)/12 );
	}
	/**
	 * MIDIノート番号の示す音階が、
	 * 指定された調（五度圏インデックス値）におけるスケール構成音に
	 * 該当するか調べます。
	 *
	 * <p>例えば、調の五度圏インデックス値を０にした場合（ハ長調またはイ短調）、
	 * 白鍵のときにtrue、黒鍵のときにfalseを返すので、鍵盤の描画にも便利です。
	 * </p>
	 *
	 * <p>キーは五度圏インデックス値しか指定しないので、
	 * マイナーキーの場合は
	 * 平行調のメジャーキーと同じスケール構成音をもつ
	 * ナチュラルマイナースケールとして判断されます。
	 * ハーモニックマイナー、メロディックマイナースケールについては、
	 * 別途それを指定する手段がないと判別できないので対応していません。
	 * </p>
	 *
	 * @param noteNo 調べたい音階のノート番号
	 * @param keyCo5 キーの五度圏インデックス値
	 * @return スケール構成音のときtrue、スケールを外れている場合false
	 */
	public static boolean isOnScale(int noteNo, int keyCo5) {
		return mod12(reverseCo5(noteNo) - keyCo5 + 1) < 7 ;
	}
	/**
	 * 五度圏インデックス値で表された音階を、
	 * 指定された半音数だけ移調した結果を返します。
	 *
	 * <p>移調する半音数が０の場合、指定の五度圏インデックス値をそのまま返します。
	 * 例えば五度圏インデックス値が +7 の場合、-5 ではなく +7 を返します。
	 * </p>
	 *
	 * @param co5 五度圏インデックス値
	 * @param chromaticOffset 移調する半音数
	 * @return 移調結果（-5 ～ 6）
	 */
	public static int transposeCo5(int co5, int chromaticOffset) {
		if( chromaticOffset == 0 ) return co5;
		int transposed_co5 = mod12( co5 + reverseCo5(chromaticOffset) );
		if( transposed_co5 > 6 ) transposed_co5 -= Music.SEMITONES_PER_OCTAVE;
		return transposed_co5; // range: -5 to +6
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

