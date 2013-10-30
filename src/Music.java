import java.awt.Color; // Color
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
// for ComboBoxModel implementation
import javax.swing.ComboBoxModel;
import javax.swing.JLabel;
import javax.swing.event.ListDataListener;

/**
 * 音楽理論・自動作曲アルゴリズムの Java 実装
 *
 * Circle-Of-Fifth based music theory functions
 *
 * @author Copyright (C) 2004-2013 ＠きよし - Akiyoshi Kamide
 */
public class Music {
	/**
	 * １オクターブの半音数
	 */
	public static final int SEMITONES_PER_OCTAVE = 12;
	/**
	 * シンボルの言語モード（音階、調など）
	 */
	public enum SymbolLanguage {
		/**
		 * シンボル表記（Bb, F#）
		 */
		SYMBOL(
			Arrays.asList("bb","b","","#","x"),
			"FCGDAEB",false,"","m"," / "
		),
		/**
		 * 英名表記（B flat, F sharp）
		 */
		NAME(
			Arrays.asList(" double flat"," flat",""," sharp"," double sharp"),
			"FCGDAEB",false," major"," minor"," / "
		),
		/**
		 * 日本名表記（変ロ, 嬰ヘ）
		 */
		IN_JAPANESE(
			Arrays.asList("重変","変","","嬰","重嬰"),
			"ヘハトニイホロ",true,"長調","短調","／"
		);
		/**
		 * ♭や♯の表記を、半音下がる数が多いほうから順に並べたリスト
		 */
		List<String> sharpFlatList;
		/**
		 * 音名を五度圏順で並べた文字列（必ず７文字でなければならない）
		 */
		String notes;
		/**
		 * 変化記号が音名の前につく（true）か後ろにつく（false）か
		 * <p>英語の場合は B♭ のように♭が後ろ、
		 * 日本語の場合は「変ロ」のように「変」が前につくことを表します。
		 * </p>
		 */
		boolean preSharpFlat;
		/**
		 * メジャーを表す文字列
		 */
		String major;
		/**
		 * マイナーを表す文字列
		 */
		String minor;
		/**
		 * メジャーとマイナーを併記する場合の区切り文字
		 */
		String majorMinorDelimiter;
		private SymbolLanguage(
			List<String> sharpFlatList,
			String notes,
			boolean preSharpFlat,
			String major, String minor, String majorMinorDelimiter
		) {
			this.sharpFlatList = sharpFlatList;
			this.notes = notes;
			this.preSharpFlat = preSharpFlat;
			this.major = major;
			this.minor = minor;
			this.majorMinorDelimiter = majorMinorDelimiter;
		}
		/**
		 * 音名の文字列を、メジャーキー基準の五度圏インデックス値に変換します。
		 *
		 * @param noteSymbol 音名の文字列
		 * @return メジャーキー基準の五度圏インデックス値
		 * @throws IllegalArgumentException
		 *  指定の音名が空、またはA～Gの範囲を外れている場合
		 */
		public int majorCo5Of(String noteSymbol) {
			if( Objects.requireNonNull(
				noteSymbol,
				"Note symbol must not be null"
			).isEmpty() ) {
				throw new IllegalArgumentException(
					"Empty note symbol specified"
				);
			}
			char topChar = noteSymbol.charAt(0);
			int co5 = notes.indexOf(topChar);
			if( co5 < 0 ) {
				throw new IllegalArgumentException(
					"Unknown note symbol " + noteSymbol
				);
			}
			co5--;
			int offset = -14;
			for( String sharpFlat : sharpFlatList ) {
				if( ! sharpFlat.isEmpty() && noteSymbol.startsWith(sharpFlat,1) ) {
					// 変化記号を発見
					// bb のほうが b よりも先にマッチするので誤判定の心配なし
					co5 += offset;
					break;
				}
				offset += 7;
			}
			return co5;
		}
	}
	/**
	 * 音名（オクターブ抜き）を表すクラスです。値は不変です。
	 *
	 * <p>この音名は、メジャーキーの調号にした場合に
	 * 「♭、＃が何個つくか」という数値
	 * 「五度圏インデックス値」で保持することを基本としています。
	 * こうすれば異名同音を明確に区別でき、
	 * しかも音楽理論的な計算を極めて単純な数式で行えるようになります。
	 * この方式はMIDIメタメッセージで調号を指定するときにも使われていて、
	 * 非常に高い親和性を持ちます。
	 * </p>
	 */
	public static class NoteSymbol implements Cloneable {
		/**
		 * メジャーキー基準の五度圏インデックス値
		 */
		private int majorCo5;
		/**
		 * ノート番号（0～11）
		 */
		private int noteNumber;
		/**
		 * 音名 C（ハ音）を構築します。
		 */
		public NoteSymbol() {
		}
		/**
		 * 五度圏インデックス値（メジャーキー基準）から音名を構築します。
		 * @param majorCo5 五度圏インデックス値
		 */
		public NoteSymbol(int majorCo5) {
			noteNumber = toNoteNumber(this.majorCo5 = majorCo5);
		}
		/**
		 * 音名を文字列から構築します。
		 * @param noteSymbol 音名の文字列
		 * @throws IllegalArgumentException
		 *  指定の音名が空、またはA～Gの範囲を外れている場合
		 */
		public NoteSymbol(String noteSymbol) {
			this(SymbolLanguage.SYMBOL.majorCo5Of(noteSymbol.trim()));
		}
		@Override
		protected NoteSymbol clone() {
			return new NoteSymbol(majorCo5);
		}
		/**
		 * この音階が指定されたオブジェクトと等しいか調べます。
		 *
		 * <p>双方の五度圏インデックス値が等しい場合のみtrueを返します。
		 * すなわち、異名同音は等しくないものとして判定されます。
		 * </p>
		 *
		 * @return この音階が指定されたオブジェクトと等しい場合true
		 */
		@Override
		public boolean equals(Object anObject) {
			if( this == anObject )
				return true;
			if( anObject instanceof NoteSymbol ) {
				NoteSymbol another = (NoteSymbol) anObject;
				return majorCo5 == another.majorCo5;
			}
			return false;
		}
		/**
		 * この音階のハッシュコード値として、
		 * 五度圏インデックス値をそのまま返します。
		 *
		 * @return この音階のハッシュコード値
		 */
		@Override
		public int hashCode() { return majorCo5; }
		/**
		 * 音階が等しいかどうかを、異名同音を無視して判定します。
		 * @param another 比較対象の音階
		 * @return 等しければtrue
		 */
		public boolean equalsEnharmonically(NoteSymbol another) {
			return this == another || this.noteNumber == another.noteNumber;
		}
		/**
		 * 五度圏インデックス値（メジャーキー基準）を返します。
		 * @return 五度圏インデックス値
		 */
		public int toCo5() { return majorCo5; }
		/**
		 * メジャーかマイナーかを指定し、対応する五度圏インデックス値を返します。
		 * <p>マイナーの場合、
		 * メジャー基準の五度圏インデックス値から３が差し引かれます。
		 * 例えば、C major の場合は調号が０個なのに対し、
		 * C minor のときは調号の♭が３個に増えますが、
		 * ３を差し引くことによってこのズレが補正されます。
		 * </p>
		 *
		 * @param isMinor マイナーのときtrue
		 * @return 五度圏インデックス値
		 */
		public int toCo5(boolean isMinor) {
			return isMinor ? majorCo5 - 3 : majorCo5;
		}
		/**
		 * ノート番号（0～11）を返します。
		 * <p>これはMIDIノート番号からオクターブ情報を抜いた値と同じです。
		 * 五度圏インデックス値をノート番号に変換した場合、
		 * 異名同音、すなわち同じ音階が♭表記、♯表記のどちらだったか
		 * という情報は失われます。
		 * </p>
		 * @return ノート番号（0～11）
		 */
		public int toNoteNumber() { return noteNumber; }
		/**
		 * この音階の文字列表現として音名を返します。
		 * @return この音階の文字列表現
		 */
		@Override
		public String toString() {
			return toStringIn(SymbolLanguage.SYMBOL, false);
		}
		/**
		 * 指定した言語モードにおける文字列表現を返します。
		 * @param language 言語モード
		 * @return 文字列表現
		 */
		public String toStringIn(SymbolLanguage language) {
			return toStringIn(language, false);
		}
		/**
		 * 指定した言語モードとメジャーマイナー種別における文字列表現を返します。
		 * <p>マイナーが指定された場合、
		 * 五度圏インデックス値を３つ進めた音階として返します。
		 * 例えば、{@link #toCo5()} の戻り値が０の場合、
		 * メジャーが指定されていれば C を返しますが、
		 * マイナーが指定されると A を返します。
		 * これにより、同じ五度圏インデックス値０で C と Am の両方のルート音を導出できます。
		 * </p>
		 * @param language 言語モード
		 * @param isMinor マイナーのときtrue
		 * @return 文字列表現
		 */
		public String toStringIn(SymbolLanguage language, boolean isMinor) {
			int co5_s771 = majorCo5 + 15; // Shift 7 + 7 + 1 = 15 steps
			if( isMinor ) {
				// When co5 is for minor (key or chord), shift 3 steps more
				co5_s771 += 3;
			}
			if( co5_s771 < 0 || co5_s771 >= 35 ) {
				//
				// ３５種類の音名の範囲に入らないような値が来てしまった場合は、
				// それを調号として見たときに 5b ～ 6# の範囲に収まるような異名同音(enharmonic)に置き換える。
				//
				co5_s771 = mod12(co5_s771);  // returns 0(Fbb) ... 7(Fb) 8(Cb) 9(Gb) 10(Db) 11(Ab)
				if( isMinor ) {
					if( co5_s771 == 0 )
						co5_s771 += SEMITONES_PER_OCTAVE * 2; // 0(Fbbm)+24 = 24(D#m)
					else
						co5_s771 += SEMITONES_PER_OCTAVE;  // 1(Cbbm)+12 = 13(Bbm)
				}
				else {
					if( co5_s771 < 10 )
						co5_s771 += SEMITONES_PER_OCTAVE;  // 0(Fbb)+12 = 12(Eb), 9(Gb)+12 = 21(F#)
				}
			}
			int sharpFlatIndex = co5_s771 / 7;
			int note_index = co5_s771 - sharpFlatIndex * 7;
			String note = language.notes.substring( note_index, note_index+1 );
			String sharp_flat = language.sharpFlatList.get(sharpFlatIndex);
			return language.preSharpFlat ? sharp_flat + note : note + sharp_flat;
		}
		/**
		 * 指定の最大文字数の範囲で、MIDIノート番号が示す音名を返します。
		 * <p>ノート番号だけでは物理的な音階情報しか得られないため、
		 * 白鍵で＃♭のついた音階表現（B#、Cb など）、
		 * ダブルシャープ、ダブルフラットを使った表現は返しません。
		 * </p>
		 * <p>白鍵の場合は A ～ G までの文字、黒鍵の場合は＃と♭の両方の表現を返します。
		 * ただし、制限文字数の指定により、＃と♭の両方を返せないことがわかった場合は、
		 * 五度圏で値０（キー C / Am）からの距離が、メジャー、マイナーの両方を含めて
		 * 近くにあるほうの表現（C# Eb F# Ab Bb）のみを返します。
		 * </p>
		 * @param noteNo MIDIノート番号
		 * @param maxChars 最大文字数
		 * @return MIDIノート番号が示す音名
		 */
		public static String noteNumberToSymbol(int noteNo, int maxChars) {
			int co5 = mod12(reverseCo5(noteNo));
			if( co5 == 11 ) {
				return (new NoteSymbol(-1)).toString();
			}
			else if( co5 >= 6 ) {
				if( maxChars >= 7 ) {
					return
						(new NoteSymbol(co5)).toString() + " / " +
						(new NoteSymbol(co5 - 12)).toString();
				}
				else {
					// String capacity not enough
					// Select only one note (sharped or flatted)
					return (new NoteSymbol(co5 - ((co5 >= 8) ? 12 : 0))).toString();
				}
			}
			else return (new NoteSymbol(co5)).toString();
		}
		/**
		 * 最大256文字の範囲で、MIDIノート番号が示す音名を返します。
		 * <p>内部的には
		 * {@link #noteNumberToSymbol(int, int)} を呼び出しているだけです。
		 * </p>
		 * @param noteNo MIDIノート番号
		 * @return MIDIノート番号が示す音名
		 */
		public static String noteNoToSymbol(int noteNo) {
			return noteNumberToSymbol(noteNo, 256);
		}
		/**
		 * 指定された五度圏インデックス値（メジャーキー基準）を
		 * ノート番号（0～11）に変換します。
		 *
		 * <p>これはMIDIノート番号からオクターブ情報を抜いた値と同じです。
		 * 五度圏インデックス値をノート番号に変換した場合、
		 * 異名同音、すなわち同じ音階が♭表記、♯表記のどちらだったか
		 * という情報は失われます。
		 * </p>
		 * @return ノート番号（0～11）
		 */
		public static int toNoteNumber(int majorCo5) {
			return mod12(reverseCo5(majorCo5));
		}
	}

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

	/**
	 * 音域を表すクラスです。
	 */
	public static class Range {
		public int min_note = 0;
		public int max_note = MIDISpec.MAX_NOTE_NO;
		public int min_key_offset = 0;
		public boolean is_inversion_mode = true;
		public Range( int min_note, int max_note ) {
			this.min_note = min_note;
			this.max_note = max_note;
		}
		public Range( Integer[] notes ) {
			if( notes == null ) return;
			switch( notes.length ) {
			case 0: return;
			case 1:
				min_note = max_note = notes[0];
				break;
			default:
				if( notes[0] > notes[1] ) {
					min_note = notes[1];
					max_note = notes[0];
				}
				else {
					min_note = notes[0];
					max_note = notes[1];
				}
				break;
			}
		}
		public Range(
			int min_note, int max_note,
			int min_key_offset, boolean inv_mode
		) {
			this.min_note = min_note;
			this.max_note = max_note;
			this.min_key_offset = min_key_offset;
			this.is_inversion_mode = inv_mode;
		}
		public int invertedNoteOf(int note_no) {
			return invertedNoteOf( note_no, null );
		}
		public int invertedNoteOf(int note_no, Key key) {
			int min_note = this.min_note;
			int max_note = this.max_note;
			int offset = 0;
			if( key != null ) {
				offset = key.relativeDo();
				if( min_key_offset < 0 && offset >= mod12(min_key_offset) ) {
					offset -= 12;
				}
				else if( min_key_offset > 0 && offset < mod12(min_key_offset) ) {
					offset += 12;
				}
				min_note += offset;
				max_note += offset;
			}
			int octave = min_note / SEMITONES_PER_OCTAVE;
			note_no += 12 * octave;
			while( note_no > max_note )
				note_no -= SEMITONES_PER_OCTAVE;
			while( note_no > MIDISpec.MAX_NOTE_NO )
				note_no -= SEMITONES_PER_OCTAVE;
			while( note_no < min_note )
				note_no += SEMITONES_PER_OCTAVE;
			while( note_no < 0 )
				note_no += SEMITONES_PER_OCTAVE;
			return note_no;
		}
		public void invertNotesOf( int[] notes, Key key ) {
			int i;
			if( is_inversion_mode ) {
				for( i=0; i<notes.length; i++ ) {
					notes[i] = invertedNoteOf( notes[i], key );
				}
			}
			else {
				int n = invertedNoteOf( notes[0], new Key(min_key_offset) );
				int n_diff = n - notes[0];
				notes[0] = n;
				for( i=1; i<notes.length; i++ ) {
					notes[i] += n_diff;
				}
			}
		}
	}

	/**
	 * 調（キー）を表すクラスです。
	 *
	 * <p>内部的には次の値を持っています。</p>
	 * <ul>
	 * <li>五度圏インデックス値。これは調号の♯の数（♭の数は負数）と同じです。</li>
	 * <li>メジャー／マイナーの区別（無指定ありの３値）</li>
	 * </ul>
	 * <p>これらの値はMIDIのメタメッセージにある調号のパラメータに対応します。
	 * </p>
	 */
	public static class Key implements Cloneable {
		/**
		 * メジャーかマイナーかが特定できていないことを示す値
		 */
		public static final int MAJOR_OR_MINOR = 0;
		/**
		 * メジャーキー（長調）
		 */
		public static final int MAJOR = 1;
		/**
		 * マイナーキー（短調）
		 */
		public static final int MINOR = -1;
		/**
		 * この調の五度圏インデックス値
		 */
		private int co5;
		/**
		 * メジャー・マイナーの種別
		 */
		private int majorMinor;
		/**
		 * 調号が空のキー（ハ長調またはイ短調）を構築します。
		 */
		public Key() { setKey(0, MAJOR_OR_MINOR); }
		/**
		 * 指定の五度圏インデックス値を持つ調を、
		 * メジャーとマイナーを指定せずに構築します。
		 *
		 * @param co5 五度圏インデックス値
		 */
		public Key(int co5) { setKey(co5, MAJOR_OR_MINOR); }
		/**
		 * 指定の五度圏インデックス値を持つ、
		 * メジャー／マイナーを指定した調を構築します。
		 *
		 * @param co5 五度圏インデックス値
		 * @param majorMinor {@link #MAJOR}、{@link #MINOR}、{@link #MAJOR_OR_MINOR} のいずれか
		 */
		public Key(int co5, int majorMinor) {
			setKey(co5, majorMinor);
		}
		/**
		 * 指定の五度圏インデックス値を持つ、
		 * メジャー／マイナーの明確な調を構築します。
		 *
		 * @param co5 五度圏インデックス値
		 * @param isMinor true:マイナー、false:メジャー
		 */
		public Key(int co5, boolean isMinor) {
			setKey(co5, isMinor);
		}
		/**
		 * MIDIの調データ（メタメッセージ2byte）から調を構築します。
		 * @param midiData MIDIの調データ
		 */
		public Key(byte midiData[]) {
			setBytes(midiData);
		}
		/**
		 * C、Am のような文字列から調を構築します。
		 * @param keySymbol キーを表す文字列
		 */
		public Key(String keySymbol) {
			boolean isMinor = keySymbol.matches(".*m");
			setKey((new NoteSymbol(keySymbol)).toCo5(isMinor), isMinor);
		}
		/**
		 * 指定されたコードと同名の調を構築します。
		 * @param chord コード（和音）
		 */
		public Key(Chord chord) {
			boolean isMinor = chord.isMinor();
			setKey(chord.rootNoteSymbol().toCo5(isMinor), isMinor);
		}
		@Override
		protected Key clone() {
			return new Key(co5, majorMinor);
		}
		@Override
		public boolean equals(Object anObject) {
			if( this == anObject )
				return true;
			if( anObject instanceof Key ) {
				Key another = (Key) anObject;
				return
					co5 == another.toCo5() &&
					majorMinor == another.majorMinor() ;
			}
			return false;
		}
		@Override
		public int hashCode() {
			return majorMinor * 256 + co5 ;
		}
		private void setKey(int co5, boolean isMinor) {
			setKey( co5, isMinor ? MINOR : MAJOR );
		}
		private void setKey(int co5, int majorMinor) {
			this.co5 = co5;
			this.majorMinor = majorMinor;
			normalize();
		}
		/**
		 * MIDIの調データ（メタメッセージ2byte）を設定します。
		 * @param data MIDIの調データ
		 */
		public void setBytes( byte[] data ) {
			byte sharpFlat = data.length > 0 ? data[0] : 0;
			byte isMinor = data.length > 1 ? data[1] : 0;
			setKey( (int)sharpFlat, isMinor==1 );
		}
		/**
		 * MIDIの調データ（メタメッセージ2byte）を生成して返します。
		 * @return  MIDIの調データ
		 */
		public byte[] getBytes() {
			byte data[] = new byte[2];
			data[0] = (byte)(co5 & 0xFF);
			data[1] = (byte)(majorMinor == MINOR ? 1 : 0);
			return data;
		}
		/**
		 * 五度圏インデックス値を返します。
		 * @return 五度圏インデックス値
		 */
		public int toCo5() { return co5; }
		/**
		 * メジャー／マイナーの区別を返します。
		 * @return {@link #MAJOR}、{@link #MINOR}、{@link #MAJOR_OR_MINOR} のいずれか
		 */
		public int majorMinor() { return majorMinor; }
		/**
		 * 相対ドの音階を返します。
		 * @return 相対ドの音階（0～11）
		 */
		public int relativeDo() {
			return NoteSymbol.toNoteNumber(co5);
		}
		/**
		 * この調のルート音を返します。
		 * メジャーキーの場合は相対ド、
		 * マイナーキーの場合は相対ラの音階です。
		 *
		 * @return キーのルート音（0～11）
		 */
		public int rootNoteNumber() {
			int n = relativeDo();
			return majorMinor==MINOR ? mod12(n-3) : n;
		}
		/**
		 * 指定されたノート番号の音が、この調のスケールの構成音か調べます。
		 * メジャーキーの場合はメジャースケール、
		 * マイナーキーの場合はナチュラルマイナースケールとして判断されます。
		 *
		 * @param noteNumber ノート番号
		 * @return 指定されたノート番号がこのキーのスケールの構成音ならtrue
		 */
		public boolean isOnScale(int noteNumber) {
			return Music.isOnScale(noteNumber, co5);
		}
		/**
		 * この調を、指定された半音オフセット値だけ移調します。
		 *
		 * @param chromaticOffset 半音オフセット値
		 * @return このオブジェクト自身（移調後）
		 */
		public Key transpose(int chromaticOffset) {
			co5 = transposeCo5(co5, chromaticOffset);
			return this;
		}
		/**
		 * この調に異名同音の調がある場合、その調に置換します。
		 * <p>例えば、♭５個（D♭メジャー）の場合は♯７個（C♯メジャー）に置換されます。
		 * 異名同音の調が存在しないキー（４♯～４♭）に対してこのメソッドを呼び出しても、
		 * 何も変化しません。
		 * </p>
		 */
		public void toggleEnharmonically() {
			if( co5 > 4 )
				co5 -= 12;
			else if( co5 < -4 )
				co5 += 12;
		}
		/**
		 * この調を正規化します。
		 * 調が７♭～７♯の範囲に入っていない場合、
		 * その範囲に入るよう調整されます。
		 */
		public void normalize() {
			if( co5 < -7 || co5 > 7 ) {
				co5 = Music.mod12( co5 );
				if( co5 > 6 ) co5 -= SEMITONES_PER_OCTAVE;
			}
		}
		/**
		 * 平行調を生成して返します。
		 * これは元の調と同じ調号を持つ、メジャーとマイナーが異なる調です。
		 *
		 * <p>メジャーとマイナーの区別が不明な場合、クローンを生成したのと同じことになります。
		 * </p>
		 *
		 * @return 平行調
		 */
		public Key relativeKey() {
			return new Key(co5, majorMinor * (-1));
		}
		/**
		 * 同主調を生成して返します。
		 * これは元の調とルート音が同じで、メジャーとマイナーが異なる調です。
		 *
		 * <p>メジャーとマイナーの区別が不明な場合、クローンを生成したのと同じことになります。
		 * 元の調の♭、♯の数が５個以上の場合、
		 * ７♭～７♯の範囲をはみ出すことがあります（正規化は行われません）。
		 * </p>
		 *
		 * @return 同主調
		 */
		public Key parallelKey() {
			switch( majorMinor ) {
			case MAJOR: return new Key( co5-3, MINOR );
			case MINOR: return new Key( co5+3, MAJOR );
			default: return new Key(co5);
			}
		}
		/**
		 * 五度圏で真裏にあたる調を生成して返します。
		 * @return 五度圏で真裏にあたる調
		 */
		public Key oppositeKey() {
			return new Key(Music.oppositeCo5(co5), majorMinor);
		}
		/**
		 * この調の文字列表現を C、Am のような形式で返します。
		 * @return この調の文字列表現
		 */
		@Override
		public String toString() {
			return toStringIn(SymbolLanguage.SYMBOL);
		}
		/**
		 * この調の文字列表現を、指定された形式で返します。
		 * @return この調の文字列表現
		 */
		public String toStringIn(SymbolLanguage language) {
			NoteSymbol note = new NoteSymbol(co5);
			String major = note.toStringIn(language, false) + language.major;
			if( majorMinor > 0 ) {
				return major;
			}
			else {
				String minor = note.toStringIn(language, true) + language.minor;
				return majorMinor < 0 ?
					minor : major + language.majorMinorDelimiter + minor ;
			}
		}
		/**
		 * 調号を表す半角文字列を返します。
		 * 正規化された状態において最大２文字になるよう調整されます。
		 *
		 * @return 調号を表す半角文字列
		 */
		public String signature() {
			switch(co5) {
			case  0: return "==";
			case  1: return "#";
			case -1: return "b";
			case  2: return "##";
			case -2: return "bb";
			default:
				if( co5 >= 3 && co5 <= 7 ) return co5 + "#" ;
				else if( co5 <= -3 && co5 >= -7 ) return (-co5) + "b" ;
				return "";
			}
		}
		/**
		 * 調号の説明（英語）を返します。
		 * @return 調号の説明
		 */
		public String signatureDescription() {
			switch(co5) {
			case  0: return "no sharps or flats";
			case  1: return "1 sharp";
			case -1: return "1 flat";
			default: return co5 < 0 ? (-co5) + " flats" : co5 + " sharps" ;
			}
		}
	}

	/**
	 * 和音（コード - musical chord）のクラス
	 */
	public static class Chord implements Cloneable {
		/**
		 * コード構成音の順序に対応する色
		 */
		public static final Color NOTE_INDEX_COLORS[] = {
			Color.red,
			new Color(0x40,0x40,0xFF),
			Color.orange.darker(),
			new Color(0x20,0x99,0x00),
			Color.magenta,
			Color.orange,
			Color.green
		};
		/**
		 * ルート音の半音差（ないのと同じ）
		 */
		public static final int ROOT = 0;
		/**
		 * 長２度の半音差
		 */
		public static final int SUS2 = 2;
		/**
		 * 短３度または増２度の半音差
		 */
		public static final int MINOR = 3;
		/**
		 * 長３度の半音差
		 */
		public static final int MAJOR = 4;
		/**
		 * 完全４度の半音差
		 */
		public static final int SUS4 = 5;	//
		/**
		 * 減５度または増４度の半音差（トライトーン ＝ 三全音 ＝ 半オクターブ）
		 */
		public static final int FLAT5 = 6;
		/**
		 * 完全５度の半音差
		 */
		public static final int PARFECT5 = 7;
		/**
		 * 増５度または短６度の半音差
		 */
		public static final int SHARP5 = 8;
		/**
		 * 長６度または減７度の半音差
		 */
		public static final int SIXTH = 9;
		/**
		 * 短７度の半音差
		 */
		public static final int SEVENTH = 10;
		/**
		 * 長７度の半音差
		 */
		public static final int MAJOR_SEVENTH = 11;
		/**
		 * 短９度（短２度の１オクターブ上）の半音差
		 */
		public static final int FLAT9 = 13;
		/**
		 * 長９度（長２度の１オクターブ上）の半音差
		 */
		public static final int NINTH = 14;
		/**
		 * 増９度（増２度の１オクターブ上）の半音差
		 */
		public static final int SHARP9 = 15;
		/**
		 * 完全１１度（完全４度の１オクターブ上）の半音差
		 */
		public static final int ELEVENTH = 17;
		/**
		 * 増１１度（増４度の１オクターブ上）の半音差
		 */
		public static final int SHARP11 = 18;
		/**
		 * 短１３度（短６度の１オクターブ上）の半音差
		 */
		public static final int FLAT13 = 20;
		/**
		 * 長１３度（長６度の１オクターブ上）の半音差
		 */
		public static final int THIRTEENTH = 21;
		//
		// index
		public static final int THIRD_OFFSET	= 0;	// ３度
		public static final int FIFTH_OFFSET	= 1;	// ５度
		public static final int SEVENTH_OFFSET	= 2;	// ７度
		public static final int NINTH_OFFSET	= 3;	// ９度
		public static final int ELEVENTH_OFFSET	= 4;	// １１度
		public static final int THIRTEENTH_OFFSET	= 5;	// １３度
		/**
		 * デフォルトの半音値（メジャーコード固定）
		 */
		public static int DEFAULT_OFFSETS[] = {
			MAJOR, PARFECT5, ROOT, ROOT, ROOT, ROOT,
		};
		/**
		 * このコードのルート音
		 */
		private NoteSymbol rootNoteSymbol;
		/**
		 * このコードのベース音（ルート音と異なる場合は分数コードの分母）
		 */
		private NoteSymbol bassNoteSymbol;
		/**
		 * 現在の半音値
		 */
		public int offsets[] =
			Arrays.copyOf(DEFAULT_OFFSETS, DEFAULT_OFFSETS.length);
		/**
		 * コード C major を構築します。
		 */
		public Chord() {
			this(new NoteSymbol());
		}
		/**
		 * 指定した音名のメジャーコードを構築します。
		 * @param noteSymbol 音名
		 */
		public Chord(NoteSymbol noteSymbol) {
			setRoot(noteSymbol);
			setBass(noteSymbol);
		}
		/**
		 * 指定された調と同名のコードを構築します。
		 * <p>元の調がマイナーキーの場合はマイナーコード、
		 * それ以外の場合はメジャーコードになります。
		 * </p>
		 * @param key 調
		 */
		public Chord(Key key) {
			int keyCo5 = key.toCo5();
			if( key.majorMinor() == MINOR ) {
				keyCo5 += 3;
				setMinorThird();
			}
			setRoot(new NoteSymbol(keyCo5));
			setBass(new NoteSymbol(keyCo5));
		}
		/**
		 * コード名の文字列からコードを構築します。
		 * @param chordSymbol コード名の文字列
		 */
		public Chord(String chordSymbol) {
			setChordSymbol(chordSymbol);
		}
		/**
		 * このコードのクローンを作成します。
		 */
		@Override
		protected Chord clone() {
			Chord newChord = new Chord(rootNoteSymbol);
			newChord.offsets = Arrays.copyOf( offsets, offsets.length );
			newChord.setBass(bassNoteSymbol);
			return newChord;
		}
		/**
		 * コードのルート音を指定された音階に置換します。
		 * @param rootNoteSymbol 音階
		 * @return このコード自身（置換後）
		 */
		public Chord setRoot(NoteSymbol rootNoteSymbol) {
			this.rootNoteSymbol = rootNoteSymbol;
			return this;
		}
		/**
		 * コードのベース音を指定された音階に置換します。
		 * @param rootNoteSymbol 音階
		 * @return このコード自身（置換後）
		 */
		public Chord setBass(NoteSymbol rootNoteSymbol) {
			this.bassNoteSymbol = rootNoteSymbol;
			return this;
		}
		// コードの種類を設定します。
		//
		public void setMajorThird() { offsets[THIRD_OFFSET] = MAJOR; }
		public void setMinorThird() { offsets[THIRD_OFFSET] = MINOR; }
		public void setSus4() { offsets[THIRD_OFFSET] = SUS4; }
		public void setSus2() { offsets[THIRD_OFFSET] = SUS2; }
		//
		public void setParfectFifth() { offsets[FIFTH_OFFSET] = PARFECT5; }
		public void setFlattedFifth() { offsets[FIFTH_OFFSET] = FLAT5; }
		public void setSharpedFifth() { offsets[FIFTH_OFFSET] = SHARP5; }
		//
		public void clearSeventh() { offsets[SEVENTH_OFFSET] = ROOT; }
		public void setMajorSeventh() { offsets[SEVENTH_OFFSET] = MAJOR_SEVENTH; }
		public void setSeventh() { offsets[SEVENTH_OFFSET] = SEVENTH; }
		public void setSixth() { offsets[SEVENTH_OFFSET] = SIXTH; }
		//
		public void clearNinth() { offsets[NINTH_OFFSET] = ROOT; }
		public void setNinth() { offsets[NINTH_OFFSET] = NINTH; }
		public void setSharpedNinth() { offsets[NINTH_OFFSET] = SHARP9; }
		public void setFlattedNinth() { offsets[NINTH_OFFSET] = FLAT9; }
		//
		public void clearEleventh() { offsets[ELEVENTH_OFFSET] = ROOT; }
		public void setEleventh() { offsets[ELEVENTH_OFFSET] = ELEVENTH; }
		public void setSharpedEleventh() { offsets[ELEVENTH_OFFSET] = SHARP11; }
		//
		public void clearThirteenth() { offsets[THIRTEENTH_OFFSET] = ROOT; }
		public void setThirteenth() { offsets[THIRTEENTH_OFFSET] = THIRTEENTH; }
		public void setFlattedThirteenth() { offsets[THIRTEENTH_OFFSET] = FLAT13; }
		//
		// コードネームの文字列が示すコードに置き換えます。
		public Chord setChordSymbol(String chord_symbol) {
			//
			// 分数コードの分子と分母に分ける
			String parts[] = chord_symbol.trim().split("(/|on)");
			if( parts.length == 0 ) {
				return this;
			}
			// ルート音とベース音を設定
			setRoot(new NoteSymbol(parts[0]));
			setBass(new NoteSymbol(parts[ parts.length > 1 ? 1 : 0 ]));
			String suffix = parts[0].replaceFirst("^[A-G][#bx]*","");
			//
			// () があれば、その中身を取り出す
			String suffixParts[] = suffix.split("[\\(\\)]");
			if( suffixParts.length == 0 ) {
				return this;
			}
			String suffixParen = "";
			if( suffixParts.length > 1 ) {
				suffixParen = suffixParts[1];
				suffix = suffixParts[0];
			}
			//
			// +5 -5 aug dim の判定
			offsets[FIFTH_OFFSET] = (
				suffix.matches( ".*(\\+5|aug|#5).*" ) ? SHARP5 :
				suffix.matches( ".*(-5|dim|b5).*" ) ? FLAT5 :
				PARFECT5
			);
			// 6 7 M7 の判定
			offsets[SEVENTH_OFFSET] = (
				suffix.matches( ".*(M7|maj7|M9|maj9).*" ) ? MAJOR_SEVENTH :
				suffix.matches( ".*(6|dim[79]).*" ) ? SIXTH :
				suffix.matches( ".*7.*" ) ? SEVENTH :
				ROOT
			);
			// マイナーの判定。maj7 と間違えないように比較
			offsets[THIRD_OFFSET] = (
				(suffix.matches( ".*m.*" ) && ! suffix.matches(".*ma.*") ) ? MINOR :
				suffix.matches( ".*sus4.*" ) ? SUS4 :
				MAJOR
			);
			// 9th の判定
			if( suffix.matches( ".*9.*" ) ) {
				offsets[NINTH_OFFSET] = NINTH;
				if( ! suffix.matches( ".*(add9|6|M9|maj9|dim9).*") ) {
					offsets[SEVENTH_OFFSET] = SEVENTH;
				}
			}
			else {
				offsets[NINTH_OFFSET] =
				offsets[ELEVENTH_OFFSET] =
				offsets[THIRTEENTH_OFFSET] = ROOT;
				//
				// () の中を , で分ける
				String parts_in_paren[] = suffixParen.split(",");
				for( String p : parts_in_paren ) {
					if( p.matches("(\\+9|#9)") ) offsets[NINTH_OFFSET] = SHARP9;
					else if( p.matches("(-9|b9)") ) offsets[NINTH_OFFSET] = FLAT9;
					else if( p.matches("9") ) offsets[NINTH_OFFSET] = NINTH;

					if( p.matches("(\\+11|#11)") ) offsets[ELEVENTH_OFFSET] = SHARP11;
					else if( p.matches("11") ) offsets[ELEVENTH_OFFSET] = ELEVENTH;

					if( p.matches("(-13|b13)") ) offsets[THIRTEENTH_OFFSET] = FLAT13;
					else if( p.matches("13") ) offsets[THIRTEENTH_OFFSET] = THIRTEENTH;

					// -5 や +5 が () の中にあっても解釈できるようにする
					if( p.matches("(-5|b5)") ) offsets[FIFTH_OFFSET] = FLAT5;
					else if( p.matches("(\\+5|#5)") ) offsets[FIFTH_OFFSET] = SHARP5;
				}
			}
			return this;
		}
		/**
		 * ルート音を返します。
		 * @return ルート音
		 */
		public NoteSymbol rootNoteSymbol() { return rootNoteSymbol; }
		/**
		 * ベース音を返します。分数コードの場合はルート音と異なります。
		 * @return ベース音
		 */
		public NoteSymbol bassNoteSymbol() { return bassNoteSymbol; }
		//
		// コードの種類を調べます。
		public boolean isMajor() { return offsets[THIRD_OFFSET] == MAJOR; }
		public boolean isMinor() { return offsets[THIRD_OFFSET] == MINOR; }
		public boolean isSus4() { return offsets[THIRD_OFFSET] == SUS4; }
		public boolean isSus2() { return offsets[THIRD_OFFSET] == SUS2; }
		//
		public boolean hasParfectFifth() { return offsets[FIFTH_OFFSET] == PARFECT5; }
		public boolean hasFlattedFifth() { return offsets[FIFTH_OFFSET] == FLAT5; }
		public boolean hasSharpedFifth() { return offsets[FIFTH_OFFSET] == SHARP5; }
		//
		public boolean hasNoSeventh() { return offsets[SEVENTH_OFFSET] == ROOT; }
		public boolean hasSeventh() { return offsets[SEVENTH_OFFSET] == SEVENTH; }
		public boolean hasMajorSeventh() { return offsets[SEVENTH_OFFSET] == MAJOR_SEVENTH; }
		public boolean hasSixth() { return offsets[SEVENTH_OFFSET] == SIXTH; }
		//
		public boolean hasNoNinth() { return offsets[NINTH_OFFSET] == ROOT; }
		public boolean hasNinth() { return offsets[NINTH_OFFSET] == NINTH; }
		public boolean hasFlattedNinth() { return offsets[NINTH_OFFSET] == FLAT9; }
		public boolean hasSharpedNinth() { return offsets[NINTH_OFFSET] == SHARP9; }
		//
		public boolean hasNoEleventh() { return offsets[ELEVENTH_OFFSET] == ROOT; }
		public boolean hasEleventh() { return offsets[ELEVENTH_OFFSET] == ELEVENTH; }
		public boolean hasSharpedEleventh() { return offsets[ELEVENTH_OFFSET] == SHARP11; }
		//
		public boolean hasNoThirteenth() { return offsets[THIRTEENTH_OFFSET] == ROOT; }
		public boolean hasThirteenth() { return offsets[THIRTEENTH_OFFSET] == THIRTEENTH; }
		public boolean hasFlattedThirteenth() { return offsets[THIRTEENTH_OFFSET] == FLAT13; }
		/**
		 * コードが等しいかどうかを判定します。
		 * @return 等しければtrue
		 */
		@Override
		public boolean equals(Object anObject) {
			if( this == anObject )
				return true;
			if( anObject instanceof Chord ) {
				Chord another = (Chord) anObject;
				if( ! rootNoteSymbol.equals(another.rootNoteSymbol) )
					return false;
				if( ! bassNoteSymbol.equals(another.bassNoteSymbol) )
					return false;
				return Arrays.equals(offsets, another.offsets);
			}
			return false;
		}
		@Override
		public int hashCode() {
			return toString().hashCode();
		}
		/**
		 * コードが等しいかどうかを、異名同音を無視して判定します。
		 * @param another 比較対象のコード
		 * @return 等しければtrue
		 */
		public boolean equalsEnharmonically(Chord another) {
			if( this == another )
				return true;
			if( another == null )
				return false;
			if( ! rootNoteSymbol.equalsEnharmonically(another.rootNoteSymbol) )
				return false;
			if( ! bassNoteSymbol.equalsEnharmonically(another.bassNoteSymbol) )
				return false;
			return Arrays.equals(offsets, another.offsets);
		}
		/**
		 * コード構成音の数を返します（ベース音は含まれません）。
		 * @return コード構成音の数
		 */
		public int numberOfNotes() {
			int n=1;
			for( int offset : offsets ) if( offset != ROOT ) n++;
			return n;
		}
		/**
		 * 指定された位置にあるノート番号を返します。
		 * @param index 位置（０をルート音とした構成音の順序）
		 * @return ノート番号（該当する音がない場合は -1）
		 */
		public int noteAt(int index) {
			int rootnote = rootNoteSymbol.toNoteNumber();
			if( index == 0 )
				return rootnote;
			int i=0;
			for( int offset : offsets )
				if( offset != ROOT && ++i == index )
					return rootnote + offset;
			return -1;
		}
		// コード構成音を格納したノート番号の配列を返します。
		//（ベース音は含まれません）
		// 音域が指定された場合、その音域に合わせたノート番号を返します。
		//
		public int[] toNoteArray() {
			return toNoteArray( (Range)null, (Key)null );
		}
		public int[] toNoteArray(Range range) {
			return toNoteArray( range, (Key)null );
		}
		public int[] toNoteArray(Range range, Key key) {
			int rootnote = rootNoteSymbol.toNoteNumber();
			int ia[] = new int[numberOfNotes()];
			int i;
			ia[i=0] = rootnote;
			for( int offset : offsets )
				if( offset != ROOT )
					ia[++i] = rootnote + offset;
			if( range != null ) range.invertNotesOf( ia, key );
			return ia;
		}
		/**
		 * MIDI ノート番号が、コードの構成音の何番目（０＝ルート音）に
		 * あるかを表すインデックス値を返します。
		 * 構成音に該当しない場合は -1 を返します。
		 * ベース音は検索されません。
		 * @param noteNo MIDIノート番号
		 * @return 構成音のインデックス値
		 */
		public int indexOf(int noteNo) {
			int relative_note = noteNo - rootNoteSymbol.toNoteNumber();
			if( mod12(relative_note) == 0 ) return 0;
			int i=0;
			for( int offset : offsets ) if( offset != ROOT ) {
				i++;
				if( mod12(relative_note - offset) == 0 )
					return i;
			}
			return -1;
		}
		// 構成音がそのキーのスケールを外れていないか調べます。
		public boolean isOnScaleInKey( Key key ) {
			return isOnScaleInKey( key.toCo5() );
		}
		public boolean isOnScaleInKey( int key_co5 ) {
			int rootnote = rootNoteSymbol.toNoteNumber();
			if( ! isOnScale( rootnote, key_co5 ) )
				return false;
			for( int offset : offsets )
				if( offset != ROOT && ! isOnScale( rootnote + offset, key_co5 ) )
					return false;
			return true;
		}
		//
		// コードを移調します。
		// 移調幅は chromatic_offset で半音単位に指定します。
		// 移調幅が０の場合、自分自身をそのまま返します。
		//
		public Chord transpose(int chromatic_offset) {
			return transpose(chromatic_offset, 0);
		}
		public Chord transpose(int chromatic_offset, Key original_key) {
			return transpose(chromatic_offset, original_key.toCo5());
		}
		public Chord transpose(int chromatic_offset, int original_key_co5) {
			if( chromatic_offset == 0 ) return this;
			int offsetCo5 = mod12(reverseCo5(chromatic_offset));
			if( offsetCo5 > 6 ) offsetCo5 -= 12;
			int key_co5   = original_key_co5 + offsetCo5;
			//
			int newRootCo5 = rootNoteSymbol.toCo5() + offsetCo5;
			int newBassCo5 = bassNoteSymbol.toCo5() + offsetCo5;
			if( key_co5 > 6 ) {
				newRootCo5 -= 12;
				newBassCo5 -= 12;
			}
			else if( key_co5 < -5 ) {
				newRootCo5 += 12;
				newBassCo5 += 12;
			}
			setRoot(new NoteSymbol(newRootCo5));
			return setBass(new NoteSymbol(newBassCo5));
		}
		/**
		 * この和音の文字列表現としてコード名を返します。
		 * @return この和音のコード名
		 */
		public String toString() {
			String chordSymbol = rootNoteSymbol + symbolSuffix();
			if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
				chordSymbol += "/" + bassNoteSymbol;
			}
			return chordSymbol;
		}
		/**
		 * コード名を HTML で返します。
		 *
		 * Swing の {@link JLabel#setText(String)} は HTML で指定できるので、
		 * 文字の大きさに変化をつけることができます。
		 *
		 * @param color_name 色のHTML表現（色名または #RRGGBB 形式）
		 * @return コード名のHTML
		 */
		public String toHtmlString(String color_name) {
			String small_tag = "<span style=\"font-size: 120%\">";
			String end_of_small_tag = "</span>";
			String root = rootNoteSymbol.toString();
			String formatted_root = (root.length() == 1) ? root + small_tag :
				root.replace("#",small_tag+"<sup>#</sup>").
				replace("b",small_tag+"<sup>b</sup>").
				replace("x",small_tag+"<sup>x</sup>");
			String formatted_bass = "";
			if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
				String bass = bassNoteSymbol.toString();
				formatted_bass = (bass.length() == 1) ? bass + small_tag :
					bass.replace("#",small_tag+"<sup>#</sup>").
					replace("b",small_tag+"<sup>b</sup>").
					replace("x",small_tag+"<sup>x</sup>");
				formatted_bass = "/" + formatted_bass + end_of_small_tag;
			}
			String suffix = symbolSuffix().
				replace("-5","<sup>-5</sup>").
				replace("+5","<sup>+5</sup>");
			return
				"<html>" +
				"<span style=\"color: " + color_name + "; font-size: 170% ; white-space: nowrap ;\">" +
				formatted_root + suffix + end_of_small_tag + formatted_bass +
				"</span>" +
				"</html>" ;
		}
		/**
		 * コードの説明（英語）を返します。
		 * @return コードの説明（英語）
		 */
		public String toName() {
			String chord_name = rootNoteSymbol.toStringIn(SymbolLanguage.NAME) + nameSuffix() ;
			if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
				chord_name += " on " + bassNoteSymbol.toStringIn(SymbolLanguage.NAME);
			}
			return chord_name;
		}
		/**
		 * コードネームの音名を除いた部分（サフィックス）を組み立てて返します。
		 * @return コードネームの音名を除いた部分
		 */
		public String symbolSuffix() {
			String suffix = ( ( offsets[THIRD_OFFSET] == MINOR ) ? "m" : "" );
			switch( offsets[SEVENTH_OFFSET] ) {
			case SIXTH:         suffix += "6";  break;
			case SEVENTH:       suffix += "7";  break;
			case MAJOR_SEVENTH: suffix += "M7"; break;
			}
			switch( offsets[THIRD_OFFSET] ) {
			case SUS4: suffix += "sus4"; break;
			case SUS2: suffix += "sus2"; break;
			default: break;
			}
			switch( offsets[FIFTH_OFFSET] ) {
			case FLAT5:  suffix += "-5"; break;
			case SHARP5: suffix += "+5"; break;
			default: break;
			}
			Vector<String> paren = new Vector<String>();
			switch( offsets[NINTH_OFFSET] ) {
			case NINTH:  paren.add("9"); break;
			case FLAT9:  paren.add("-9"); break;
			case SHARP9: paren.add("+9"); break;
			}
			switch( offsets[ELEVENTH_OFFSET] ) {
			case ELEVENTH: paren.add("11"); break;
			case SHARP11:  paren.add("+11"); break;
			}
			switch( offsets[THIRTEENTH_OFFSET] ) {
			case THIRTEENTH: paren.add("13"); break;
			case FLAT13:     paren.add("-13"); break;
			}
			if( paren.size() > 0 ) {
				boolean is_first = true;
				suffix += "(";
				for( String p : paren ) {
					if( is_first )
						is_first = false;
					else
						suffix += ",";
					suffix += p;
				}
				suffix += ")";
			}
			if( suffix.equals("m-5") ) return "dim";
			else if( suffix.equals("+5") ) return "aug";
			else if( suffix.equals("m6-5") ) return "dim7";
			else if( suffix.equals("(9)") ) return "add9";
			else if( suffix.equals("7(9)") ) return "9";
			else if( suffix.equals("M7(9)") ) return "M9";
			else if( suffix.equals("7+5") ) return "aug7";
			else if( suffix.equals("m6-5(9)") ) return "dim9";
			else return suffix ;
		}
		/**
		 * コードの説明のうち、音名を除いた部分を組み立てて返します。
		 * @return コード説明の音名を除いた部分
		 */
		public String nameSuffix() {
			String suffix = "";
			if( offsets[THIRD_OFFSET] == MINOR ) suffix += " minor";
			switch( offsets[SEVENTH_OFFSET] ) {
			case SIXTH:         suffix += " 6th"; break;
			case SEVENTH:       suffix += " 7th"; break;
			case MAJOR_SEVENTH: suffix += " major 7th"; break;
			}
			switch( offsets[THIRD_OFFSET] ) {
			case SUS4: suffix += " suspended 4th"; break;
			case SUS2: suffix += " suspended 2nd"; break;
			default: break;
			}
			switch( offsets[FIFTH_OFFSET] ) {
			case FLAT5 : suffix += " flatted 5th"; break;
			case SHARP5: suffix += " sharped 5th"; break;
			default: break;
			}
			Vector<String> paren = new Vector<String>();
			switch( offsets[NINTH_OFFSET] ) {
			case NINTH:  paren.add("9th"); break;
			case FLAT9:  paren.add("flatted 9th"); break;
			case SHARP9: paren.add("sharped 9th"); break;
			}
			switch( offsets[ELEVENTH_OFFSET] ) {
			case ELEVENTH: paren.add("11th"); break;
			case SHARP11:  paren.add("sharped 11th"); break;
			}
			switch( offsets[THIRTEENTH_OFFSET] ) {
			case THIRTEENTH: paren.add("13th"); break;
			case FLAT13:     paren.add("flatted 13th"); break;
			}
			if( paren.size() > 0 ) {
				boolean is_first = true;
				suffix += "(additional ";
				for( String p : paren ) {
					if( is_first )
						is_first = false;
					else
						suffix += ",";
					suffix += p;
				}
				suffix += ")";
			}
			if( suffix.equals(" minor flatted 5th") ) return " diminished (triad)";
			else if( suffix.equals(" sharped 5th") ) return " augumented";
			else if( suffix.equals(" minor 6th flatted 5th") ) return " diminished 7th";
			else if( suffix.equals(" 7th(additional 9th)") ) return " 9th";
			else if( suffix.equals(" major 7th(additional 9th)") ) return " major 9th";
			else if( suffix.equals(" 7th sharped 5th") ) return " augumented 7th";
			else if( suffix.equals(" minor 6th flatted 5th(additional 9th)") ) return " diminished 9th";
			else if( suffix.isEmpty() ) return " major";
			else return suffix ;
		}
	}

	/**
	 * Chord Progression - コード進行のクラス
	 */
	public static class ChordProgression {

		public class TickRange implements Cloneable {
			long start_tick_pos = 0, end_tick_pos = 0;
			public TickRange( long tick_pos ) {
				end_tick_pos = start_tick_pos = tick_pos;
			}
			public TickRange( long start_tick_pos, long end_tick_pos ) {
				this.start_tick_pos = start_tick_pos;
				this.end_tick_pos = end_tick_pos;
			}
			protected TickRange clone() {
				return new TickRange( start_tick_pos, end_tick_pos );
			}
			public void moveForward() {
				start_tick_pos = end_tick_pos;
			}
			public void moveForward( long duration ) {
				start_tick_pos = end_tick_pos;
				end_tick_pos += duration;
			}
			public long duration() {
				return end_tick_pos - start_tick_pos;
			}
			public boolean contains( long tick ) {
				return ( tick >= start_tick_pos && tick < end_tick_pos );
			}
		}

		private class ChordStroke {
			Chord chord; int beat_length; TickRange tick_range = null;
			public ChordStroke(Chord chord) { this( chord, 1 ); }
			public ChordStroke(Chord chord, int beat_length) {
				this.chord = chord;
				this.beat_length = beat_length;
			}
			public String toString() {
				String str = chord.toString();
				for( int i=2; i <= beat_length; i++ ) str += " %";
				return str;
			}
		}

		// 時間位置付き歌詞
		public class Lyrics {
			String text = null;
			Long start_tick_pos = null;
			public Lyrics(String text) { this.text = text; }
			public Lyrics(String text, long tick_pos) {
				this.text = text; start_tick_pos = tick_pos;
			}
			public String toString() { return text; }
		}

		private class Measure extends Vector<Object> {
			Long ticks_per_beat = null;
			public int numberOfBeats() {
				int n = 0;
				for( Object obj : this ) {
					if( obj instanceof ChordStroke ) {
						n += ((ChordStroke)obj).beat_length;
					}
				}
				return n;
			}
			// 小節内のコードストロークが時間的に等間隔かどうか調べる。
			// もし等間隔の場合、テキスト出力時に % をつける必要がなくなる。
			public boolean isEquallyDivided() {
				int l, l_prev = 0;
				for( Object obj : this ) {
					if( obj instanceof ChordStroke ) {
						l = ((ChordStroke)obj).beat_length;
						if( l_prev > 0 && l_prev != l ) {
							return false;
						}
						l_prev = l;
					}
				}
				return true;
			}
			public int addBeat() { return addBeat(1); }
			public int addBeat(int num_beats) {
				ChordStroke last_chord_stroke = null;
				for( Object obj : this ) {
					if( obj instanceof ChordStroke ) {
						last_chord_stroke = (ChordStroke)obj;
					}
				}
				if( last_chord_stroke == null ) {
					return 0;
				}
				return last_chord_stroke.beat_length += num_beats;
			}
			public String toString() {
				String str = "";
				boolean is_eq_dev = isEquallyDivided();
				for( Object element : this ) {
					str += " ";
					if( element instanceof ChordStroke ) {
						ChordStroke cs = (ChordStroke)element;
						str += is_eq_dev ? cs.chord : cs;
					}
					else if( element instanceof Lyrics ) {
						str += element.toString();
					}
				}
				return str;
			}
			public TickRange getRange() {
				long start_tick_pos = -1;
				long end_tick_pos = -1;
				for( Object element : this ) {
					if( ! (element instanceof ChordProgression.ChordStroke) )
						continue;
					ChordProgression.ChordStroke chord_stroke
					= (ChordProgression.ChordStroke)element;
					// 小節の先頭と末尾の tick を求める
					if( start_tick_pos < 0 ) {
						start_tick_pos = chord_stroke.tick_range.start_tick_pos;
					}
					end_tick_pos = chord_stroke.tick_range.end_tick_pos;
				}
				if( start_tick_pos < 0 || end_tick_pos < 0 ) {
					return null;
				}
				return new TickRange( start_tick_pos, end_tick_pos );
			}
			public ChordStroke chordStrokeAt( long tick ) {
				for( Object element : this ) {
					if( ! (element instanceof ChordProgression.ChordStroke) )
						continue;
					ChordProgression.ChordStroke chord_stroke
					= (ChordProgression.ChordStroke)element;
					if( chord_stroke.tick_range.contains(tick) ) {
						return chord_stroke;
					}
				}
				return null;
			}
		}
		private class Line extends Vector<Measure> {
			public String toString() {
				String str = "";
				for( Measure measure : this ) str += measure + "|";
				return str;
			}
		}

		// 内部変数
		private Vector<Line> lines = null;
		private Key key = null;
		private Long ticks_per_measure = null;

		public Key getKey() { return key; }
		public void setKey(Key key) { this.key = key; }

		public String toString() {
			String str = "";
			if( key != null ) str += "Key: " + key + "\n";
			for( Line line : lines ) str += line + "\n";
			return str;
		}

		// 指定された小節数、キー、拍子に合わせたコード進行をランダムに自動生成
		//
		public ChordProgression() { }
		public ChordProgression( int measure_length, int timesig_upper ) {
			int key_co5 = (int)(Math.random() * 12) - 5;
			key = new Key( key_co5, Key.MAJOR );
			lines = new Vector<Line>();
			Line line = new Line();
			boolean is_end;
			Chord chord, prev_chord = new Chord(new NoteSymbol(key_co5));
			int co5_offset, prev_co5_offset;
			double r;
			for( int mp=0; mp<measure_length; mp++ ) {
				is_end = (mp == 0 || mp == measure_length - 1); // 最初または最後の小節かを覚えておく
				Measure measure = new Measure();
				ChordStroke last_chord_stroke = null;
				for( int i=0; i<timesig_upper; i++ ) {
					if(
						i % 4 == 2 && Math.random() < 0.8
						||
						i % 2 != 0 && Math.random() < 0.9
					){
						// もう一拍延長
						last_chord_stroke.beat_length++;
						continue;
					}
					chord = new Chord(new NoteSymbol(key_co5));
					co5_offset = 0;
					prev_co5_offset = prev_chord.rootNoteSymbol().toCo5() - key_co5;
					if( ! is_end ) {
						//
						// 最初または最後の小節は常にトニックにする。
						// 完全五度ずつ下がる進行を基本としつつ、時々そうでない進行も出現するようにする。
						// サブドミナントを超えるとスケールを外れるので、超えそうになったらランダムに決め直す。
						//
						r = Math.random();
						co5_offset = prev_co5_offset - 1;
						if( co5_offset < -1 || (prev_co5_offset < 5 && r < 0.5) ) {
							//
							// 長７度がルートとなるコードの出現確率を半減させながらコードを決める
							// （余りが６のときだけが長７度）
							// なお、前回と同じコードは使わないようにする。
							do {
								co5_offset = (int)(Math.random() * 13) % 7 - 1;
							} while( co5_offset == prev_co5_offset );
						}
						int co5RootNote = key_co5 + co5_offset;
						chord.setRoot(new NoteSymbol(co5RootNote));
						chord.setBass(new NoteSymbol(co5RootNote));
					}
					switch( co5_offset ) {
					// ルート音ごとに、7th などの付加や、メジャーマイナー反転を行う確率を決める
					case 5: // VII
						if( Math.random() < 0.5 ) {
							// m7-5
							chord.setMinorThird();
							chord.setFlattedFifth();
						}
						if( Math.random() < 0.8 ) chord.setSeventh();
						break;
					case 4: // Secondary dominant (III)
						if( prev_co5_offset == 5 ) {
							// ルートが長７度→長３度の進行のとき、反転確率を上げる。
							// （ハ長調でいう Bm7-5 の次に E7 を出現しやすくする）
							if( Math.random() < 0.2 ) chord.setMinorThird();
						}
						else {
							if( Math.random() < 0.8 ) chord.setMinorThird();
						}
						if( Math.random() < 0.7 ) chord.setSeventh();
						break;
					case 3: // VI
						if( Math.random() < 0.8 ) chord.setMinorThird();
						if( Math.random() < 0.7 ) chord.setSeventh();
						break;
					case 2: // II
						if( Math.random() < 0.8 ) chord.setMinorThird();
						if( Math.random() < 0.7 ) chord.setSeventh();
						break;
					case 1: // Dominant (V)
						if( Math.random() < 0.1 ) chord.setMinorThird();
						if( Math.random() < 0.3 ) chord.setSeventh();
						if( Math.random() < 0.2 ) chord.setNinth();
						break;
					case 0: // Tonic（ここでマイナーで終わるとさみしいので setMinorThird() はしない）
						if( Math.random() < 0.2 ) chord.setMajorSeventh();
						if( Math.random() < 0.2 ) chord.setNinth();
						break;
					case -1: // Sub-dominant (IV)
						if( Math.random() < 0.1 ) {
							chord.setMinorThird();
							if( Math.random() < 0.3 ) chord.setSeventh();
						}
						else
							if( Math.random() < 0.2 ) chord.setMajorSeventh();
						if( Math.random() < 0.2 ) chord.setNinth();
						break;
					}
					measure.add( last_chord_stroke = new ChordStroke(chord) );
					prev_chord = chord;
				}
				line.add(measure);
				if( (mp+1) % 8 == 0 ) { // ８小節おきに改行
					lines.add(line);
					line = new Line();
				}
			}
			if( line.size() > 0 ) lines.add(line);
		}
		// テキストからコード進行を生成
		public ChordProgression( String source_text ) {
			if( source_text == null ) return;
			Measure measure;
			Line line;
			String[] lines_src, measures_src, elements_src;
			Chord last_chord = null;
			String key_regex = "^Key(\\s*):(\\s*)";
			//
			// キーであるかどうか見分けるためのパターン
			Pattern key_match_pattern = Pattern.compile(
					key_regex+".*$", Pattern.CASE_INSENSITIVE
					);
			// キーのヘッダーを取り除くためのパターン
			Pattern key_repl_pattern = Pattern.compile(
					key_regex, Pattern.CASE_INSENSITIVE
					);
			//
			lines_src = source_text.split("[\r\n]+");
			lines = new Vector<Line>();
			for( String line_src : lines_src ) {
				measures_src = line_src.split("\\|");
				if( measures_src.length > 0 ) {
					String key_string = measures_src[0].trim();
					if( key_match_pattern.matcher(key_string).matches() ) {
						key = new Key(
								key_repl_pattern.matcher(key_string).replaceFirst("")
								);
						// System.out.println("Key = " + key);
						continue;
					}
				}
				line = new Line();
				for( String measure_src : measures_src ) {
					elements_src = measure_src.split("[ \t]+");
					measure = new Measure();
					for( String element_src : elements_src ) {
						if( element_src.isEmpty() ) continue;
						if( element_src.equals("%") ) {
							if( measure.addBeat() == 0 ) {
								measure.add( new ChordStroke(last_chord) );
							}
							continue;
						}
						try {
							measure.add( new ChordStroke(
									last_chord = new Music.Chord(element_src)
									));
						} catch( IllegalArgumentException ex ) {
							measure.add( new Lyrics(element_src) );
						}
					}
					line.add(measure);
				}
				lines.add(line);
			}
		}

		// Major/minor 切り替え
		public void toggleKeyMajorMinor() {
			key = key.relativeKey();
		}

		// コード進行の移調
		public void transpose(int chromatic_offset) {
			for( Line line : lines ) {
				for( Measure measure : line ) {
					for( int i=0; i<measure.size(); i++ ) {
						Object element = measure.get(i);
						if( element instanceof ChordStroke ) {
							ChordStroke cs = (ChordStroke)element;
							Chord new_chord = cs.chord.clone();
							//
							// キーが未設定のときは、最初のコードから推測して設定
							if( key == null ) key = new Key( new_chord );
							//
							new_chord.transpose( chromatic_offset, key );
							measure.set( i, new ChordStroke( new_chord, cs.beat_length ) );
						}
					}
				}
			}
			key.transpose(chromatic_offset);
		}
		// 異名同音の♭と＃を切り替える
		public void toggleEnharmonically() {
			if( key == null ) return;
			int original_key_co5 = key.toCo5();
			int co5Offset = 0;
			if( original_key_co5 > 4 ) {
				co5Offset = -SEMITONES_PER_OCTAVE;
			}
			else if( original_key_co5 < -4 ) {
				co5Offset = SEMITONES_PER_OCTAVE;
			}
			else {
				return;
			}
			key.toggleEnharmonically();
			for( Line line : lines ) {
				for( Measure measure : line ) {
					for( int i=0; i<measure.size(); i++ ) {
						Object element = measure.get(i);
						if( element instanceof ChordStroke ) {
							ChordStroke cs = (ChordStroke)element;
							Chord newChord = cs.chord.clone();
							newChord.setRoot(new NoteSymbol(newChord.rootNoteSymbol().toCo5() + co5Offset));
							newChord.setBass(new NoteSymbol(newChord.bassNoteSymbol().toCo5() + co5Offset));
							measure.set( i, new ChordStroke( newChord, cs.beat_length ) );
						}
					}
				}
			}
		}
		// コード進行の中に時間軸（MIDI tick）を書き込む
		//
		public void setTickPositions( FirstTrackSpec first_track ) {
			ticks_per_measure = first_track.ticks_per_measure;
			TickRange tick_range = new TickRange(
					first_track.pre_measures * ticks_per_measure
					);
			for( Line line : lines ) { // 行単位の処理
				for( Measure measure : line ) { // 小節単位の処理
					int n_beats = measure.numberOfBeats();
					if( n_beats == 0 ) continue;
					long tpb = measure.ticks_per_beat = ticks_per_measure / n_beats ;
					for( Object element : measure ) {
						if( element instanceof Lyrics ) {
							((Lyrics)element).start_tick_pos = tick_range.start_tick_pos;
							continue;
						}
						else if( element instanceof ChordStroke ) {
							ChordStroke chord_stroke = (ChordStroke)element;
							tick_range.moveForward( tpb * chord_stroke.beat_length );
							chord_stroke.tick_range = tick_range.clone();
						}
					}
				}
			}
		}
		// コード文字列の書き込み
		public void setChordSymbolTextTo( AbstractTrackSpec ts ) {
			for( Line line : lines ) {
				for( Measure measure : line ) {
					if( measure.ticks_per_beat == null ) continue;
					for( Object element : measure ) {
						if( element instanceof ChordStroke ) {
							ts.addStringTo( 0x01, (ChordStroke)element );
						}
					}
				}
			}
		}
		// 歌詞の書き込み
		public void setLyricsTo( AbstractTrackSpec ts ) {
			for( Line line : lines ) {
				for( Measure measure : line ) {
					if( measure.ticks_per_beat == null ) continue;
					for( Object element : measure ) {
						if( element instanceof Lyrics ) {
							ts.addStringTo( 0x05, (Lyrics)element );
						}
					}
				}
			}
		}
		// コード進行をもとに MIDI シーケンスを生成
		//
		public Sequence toMidiSequence() {
			return toMidiSequence(48);
		}
		public Sequence toMidiSequence( int ppq ) {
			//
			// PPQ = Pulse Per Quarter (TPQN = Tick Per Quearter Note)
			//
			return toMidiSequence( ppq, 0, 0, null, null );
		}
		public Sequence toMidiSequence(
				int ppq, int start_measure_pos, int end_measure_pos,
				FirstTrackSpec first_track,
				Vector<AbstractNoteTrackSpec> track_specs
				) {
			// MIDIシーケンスの生成
			Sequence seq;
			try {
				seq = new Sequence(Sequence.PPQ, ppq);
			} catch ( InvalidMidiDataException e ) {
				e.printStackTrace();
				return null;
			}
			// マスタートラックの生成
			if( first_track == null ) {
				first_track = new FirstTrackSpec();
			}
			first_track.key = this.key;
			first_track.createTrack( seq, start_measure_pos, end_measure_pos );
			//
			// 中身がなければここで終了
			if( lines == null || track_specs == null ) return seq;
			//
			// コード進行の中に時間軸（MIDI tick）を書き込む
			setTickPositions( first_track );
			//
			// コードのテキストと歌詞を書き込む
			setChordSymbolTextTo( first_track );
			setLyricsTo( first_track );
			//
			// 残りのトラックを生成
			for( AbstractNoteTrackSpec ts : track_specs ) {
				ts.createTrack( seq, first_track );
				if( ts instanceof DrumTrackSpec ) {
					((DrumTrackSpec)ts).addDrums(this);
				}
				else {
					((MelodyTrackSpec)ts).addChords(this);
				}
			}
			return seq;
		}
	}

	/**
	 * MIDIトラックの仕様を表すクラス
	 */
	public static abstract class AbstractTrackSpec {
		public static final int BEAT_RESOLUTION = 2;
		// 最短の音符の長さ（四分音符を何回半分にするか）
		String name = null;
		Track track = null;
		FirstTrackSpec first_track_spec = null;
		Sequence sequence = null;
		long min_note_ticks = 0;
		int pre_measures = 2;
		public AbstractTrackSpec() { }
		public AbstractTrackSpec(String name) {
			this.name = name;
		}
		public String toString() {
			return name;
		}
		public Track createTrack( Sequence seq, FirstTrackSpec first_track_spec ) {
			this.first_track_spec = first_track_spec;
			track = (sequence = seq).createTrack();
			if( name != null ) addStringTo( 0x03, name, 0 );
			min_note_ticks = (long)( seq.getResolution() >> 2 );
			return track;
		}
		public boolean addMetaEventTo( int type, byte data[], long tick_pos  ) {
			MetaMessage meta_msg = new MetaMessage();
			try {
				meta_msg.setMessage( type, data, data.length );
			} catch( InvalidMidiDataException ex ) {
				ex.printStackTrace();
				return false;
			}
			return track.add(new MidiEvent( (MidiMessage)meta_msg, tick_pos ));
		}
		public boolean addStringTo( int type, String str, long tick_pos ) {
			if( str == null ) str = "";
			return addMetaEventTo( type, str.getBytes(), tick_pos );
		}
		public boolean addStringTo( int type, ChordProgression.ChordStroke cs ) {
			return addStringTo(
					type,
					cs.chord.toString(),
					cs.tick_range.start_tick_pos
					);
		}
		public boolean addStringTo( int type, ChordProgression.Lyrics lyrics ) {
			return addStringTo(
					type, lyrics.text, lyrics.start_tick_pos
					);
		}
		public boolean addEOT( long tick_pos ) {
			return addMetaEventTo( 0x2F, new byte[0], tick_pos );
		}
		public void setChordSymbolText( ChordProgression cp ) {
			cp.setChordSymbolTextTo( this );
		}
	}

	// 最初のトラック専用
	//
	public static class FirstTrackSpec extends AbstractTrackSpec {
		static byte default_tempo_data[] = { 0x07, (byte)0xA1, 0x20 };	// 120[QPM]
		static byte default_timesig_data[] = { 0x04, 0x02, 0x18, 0x08 };	// 4/4
		byte tempo_data[] = default_tempo_data;
		byte timesig_data[] = default_timesig_data;
		Key key = null;
		long ticks_per_measure;
		public FirstTrackSpec() { }
		public FirstTrackSpec(String name) {
			this.name = name;
		}
		public FirstTrackSpec(
			String name, byte[] tempo_data, byte[] timesig_data
		) {
			this.name = name;
			if( tempo_data != null ) this.tempo_data = tempo_data;
			if( timesig_data != null ) this.timesig_data = timesig_data;
		}
		public FirstTrackSpec(
			String name, byte[] tempo_data, byte[] timesig_data, Key key
		) {
			this(name,tempo_data,timesig_data);
			this.key = key;
		}
		public Track createTrack(Sequence seq) {
			return createTrack( seq, 0, 0 );
		}
		public Track createTrack(
			Sequence seq, int start_measure_pos, int end_measure_pos
		) {
			this.pre_measures = start_measure_pos - 1;
			Track track = super.createTrack( seq, this );
			addTempo(
				this.tempo_data = (
					tempo_data == null ? default_tempo_data : tempo_data
				), 0
			);
			addTimeSignature(
				this.timesig_data = (
					timesig_data == null ? default_timesig_data : timesig_data
				), 0
			);
			if( key != null ) addKeySignature( key, 0 );
			ticks_per_measure = (long)(
				( 4 * seq.getResolution() * this.timesig_data[0] ) >> this.timesig_data[1]
			);
			addEOT( end_measure_pos * ticks_per_measure );
			return track;
		}
		public boolean addKeySignature( Key key, long tick_pos ) {
			return addMetaEventTo( 0x59, key.getBytes(), tick_pos );
		}
		public boolean addTempo( byte data[], long tick_pos ) {
			return addMetaEventTo( 0x51, data, tick_pos );
		}
		public boolean addTimeSignature( byte data[], long tick_pos ) {
			return addMetaEventTo( 0x58, data, tick_pos );
		}
	}

	// 一般のトラック（メロディ、ドラム共通）
	//
	public static abstract class AbstractNoteTrackSpec extends AbstractTrackSpec {
		int midi_channel = -1;
		int program_no = -1;
		int velocity = 64;

		public AbstractNoteTrackSpec() {}
		public AbstractNoteTrackSpec(int ch) {
			midi_channel = ch;
		}
		public AbstractNoteTrackSpec(int ch, String name) {
			midi_channel = ch;
			this.name = name;
		}
		public AbstractNoteTrackSpec(int ch, String name, int program_no) {
			this(ch,name);
			this.program_no = program_no;
		}
		public AbstractNoteTrackSpec(int ch, String name, int program_no, int velocity) {
			this(ch,name,program_no);
			this.velocity = velocity;
		}
		public Track createTrack( Sequence seq, FirstTrackSpec first_track_spec ) {
			Track track = super.createTrack( seq, first_track_spec );
			if( program_no >= 0 ) addProgram( program_no, 0 );
			return track;
		}
		public boolean addProgram( int program_no, long tick_pos ) {
			ShortMessage short_msg;
			try {
				(short_msg = new ShortMessage()).setMessage(
					ShortMessage.PROGRAM_CHANGE, midi_channel, program_no, 0
				);
			} catch( InvalidMidiDataException ex ) {
				ex.printStackTrace();
				return false;
			}
			return track.add(new MidiEvent( (MidiMessage)short_msg, tick_pos ));
		}
		public boolean addNote(long start_tick_pos, long end_tick_pos, int note_no) {
			return addNote(start_tick_pos, end_tick_pos, note_no, velocity);
		}
		public boolean addNote(
			long start_tick_pos, long end_tick_pos,
			int note_no, int velocity
		) {
			ShortMessage short_msg;
			//
			try {
				(short_msg = new ShortMessage()).setMessage(
					ShortMessage.NOTE_ON, midi_channel, note_no, velocity
				);
			} catch( InvalidMidiDataException ex ) {
				ex.printStackTrace();
				return false;
			}
			if( ! track.add(new MidiEvent( (MidiMessage)short_msg, start_tick_pos )) )
				return false;
			//
			try {
				(short_msg = new ShortMessage()).setMessage(
						ShortMessage.NOTE_OFF, midi_channel, note_no, velocity
						);
			} catch( InvalidMidiDataException ex ) {
				ex.printStackTrace();
				return false;
			}
			return track.add( new MidiEvent( (MidiMessage)short_msg, end_tick_pos ) );
		}
	}

	public static class DrumTrackSpec extends AbstractNoteTrackSpec {
		static int default_percussions[] = { // ドラムの音色リスト
			36, // Bass Drum 1
			44, // Pedal Hi-Hat
			39, // Hand Clap
			48, // Hi Mid Tom
			50, // High Tom
			38, // Acoustic Snare
			62, // Mute Hi Conga
			63, // Open Hi Conga
		};
		PercussionComboBoxModel models[]
			= new PercussionComboBoxModel[default_percussions.length];
		int[] beat_patterns = {
			0x8888, 0x2222, 0x0008, 0x0800,
			0, 0, 0, 0
		};
		public class PercussionComboBoxModel implements ComboBoxModel<String> {
			private int note_no;
			public PercussionComboBoxModel(int default_note_no) {
				note_no = default_note_no;
			}
			// ComboBoxModel
			public Object getSelectedItem() {
				return note_no + ": " +
					MIDISpec.PERCUSSION_NAMES[note_no - MIDISpec.MIN_PERCUSSION_NUMBER];
			}
			public void setSelectedItem(Object anItem) {
				String name = (String)anItem;
				int i = MIDISpec.MIN_PERCUSSION_NUMBER;
				for( String pname : MIDISpec.PERCUSSION_NAMES ) {
					if( name.equals(i + ": " + pname) ) {
						note_no = i; return;
					}
					i++;
				}
			}
			// ListModel
			public String getElementAt(int index) {
				return (index + MIDISpec.MIN_PERCUSSION_NUMBER) + ": "
						+ MIDISpec.PERCUSSION_NAMES[index];
			}
			public int getSize() {
				return MIDISpec.PERCUSSION_NAMES.length;
			}
			public void addListDataListener(ListDataListener l) { }
			public void removeListDataListener(ListDataListener l) { }
			// Methods
			public int getSelectedNoteNo() {
				return note_no;
			}
			public void setSelectedNoteNo(int note_no) {
				this.note_no = note_no;
			}
		}

		public DrumTrackSpec(int ch, String name) {
			super(ch,name);
			for( int i=0; i<default_percussions.length; i++ ) {
				models[i] = new PercussionComboBoxModel(default_percussions[i]);
			}
		}

		public void addDrums( ChordProgression cp ) {
			int i;
			long tick;
			for( ChordProgression.Line line : cp.lines ) { // 行単位の処理
				for( ChordProgression.Measure measure : line ) { // 小節単位の処理
					if( measure.ticks_per_beat == null )
						continue;
					ChordProgression.TickRange range = measure.getRange();
					int mask;
					for(
							tick = range.start_tick_pos, mask = 0x8000;
							tick < range.end_tick_pos;
							tick += min_note_ticks, mask >>>= 1
							) {
						for( i=0; i<beat_patterns.length; i++ ) {
							if( (beat_patterns[i] & mask) == 0 )
								continue;
							addNote(
									tick, tick+10,
									models[i].getSelectedNoteNo(),
									velocity
									);
						}
					}
				}
			}
		}
	}

	public static class MelodyTrackSpec extends AbstractNoteTrackSpec {

		Range range; // 音域

		// 音を出すかどうかを表すビット列
		int beat_pattern = 0xFFFF;

		// あとで音を出し続けるかどうかを表すビット列
		int continuous_beat_pattern = 0xEEEE;

		// ベース音を使う場合 true
		// それ以外のコード構成音を使う場合 false
		boolean is_bass = false;

		// 乱数メロディを作るかどうか
		boolean random_melody = false;

		// 乱数歌詞を作るかどうか
		boolean random_lyric = false;

		public MelodyTrackSpec(int ch, String name) {
			super(ch,name);
			range = new Range(
				SEMITONES_PER_OCTAVE * 5, SEMITONES_PER_OCTAVE * 6 );
		}
		public MelodyTrackSpec(int ch, String name, Range range) {
			super(ch,name);
			this.range = range;
		}

		public void addChords( ChordProgression cp ) {
			int mask;
			long tick;
			long start_tick_pos;

			// 音階ごとの生起確率を決める重みリスト（random_melody の場合）
			int i, note_no, prev_note_no = 1;
			int note_weights[] = new int[range.max_note - range.min_note];
			//
			Key key = cp.key;
			if( key == null ) key = new Key("C");

			for( ChordProgression.Line line : cp.lines ) { // 行単位の処理
				for( ChordProgression.Measure measure : line ) { // 小節単位の処理
					if( measure.ticks_per_beat == null )
						continue;
					ChordProgression.TickRange tick_range = measure.getRange();
					boolean is_note_on = false;
					//
					// 各ビートごとに繰り返し
					for(
							tick = start_tick_pos = tick_range.start_tick_pos, mask = 0x8000;
							tick < tick_range.end_tick_pos;
							tick += min_note_ticks, mask >>>= 1
							) {
						// そのtick地点のコードを調べる
						Chord chord = measure.chordStrokeAt(tick).chord;
						int notes[] = chord.toNoteArray(range);
						//
						// 各音階ごとに繰り返し
						if( Math.random() < 0.9 ) {
							if( (beat_pattern & mask) == 0 ) {
								// 音を出さない
								continue;
							}
						}
						else {
							// ランダムに逆パターン
							if( (beat_pattern & mask) != 0 ) {
								continue;
							}
						}
						if( ! is_note_on ) {
							// 前回のビートで継続していなかったので、
							// この地点で音を出し始めることにする。
							start_tick_pos = tick;
							is_note_on = true;
						}
						if( Math.random() < 0.9 ) {
							if( (continuous_beat_pattern & mask) != 0 ) {
								// 音を継続
								continue;
							}
						}
						else {
							// ランダムに逆パターン
							if( (continuous_beat_pattern & mask) == 0 ) {
								continue;
							}
						}
						// このビートの終了tickで音を出し終わることにする。
						if( random_melody ) {
							// 音階ごとに出現確率を決める
							int total_weight = 0;
							for( i=0; i<note_weights.length; i++ ) {
								note_no = range.min_note + i;
								int m12 = mod12(note_no - chord.rootNoteSymbol().toNoteNumber());
								int w;
								if( chord.indexOf(note_no) >= 0 ) {
									// コード構成音は確率を上げる
									w = 255;
								}
								else {
									switch( m12 ) {
									case 2: // 長２度
									case 9: // 長６度
										w = 63; break;
									case 5: // 完全４度
									case 11: // 長７度
										w = 47; break;
									default:
										w = 0; break;
									}
									if( ! key.isOnScale( note_no ) ) {
										// スケールを外れている音は採用しない
										w = 0;
									}
								}
								// 乱高下軽減のため、前回との差によって確率を調整する
								int diff = note_no - prev_note_no;
								if( diff < 0 ) diff = -diff;
								if( diff == 0 ) w /= 8;
								else if( diff > 7 ) w = 0;
								else if( diff > 4 ) w /= 8;
								total_weight += (note_weights[i] = w);
							}
							// さいころを振って音階を決定
							note_no = range.invertedNoteOf(key.rootNoteNumber());
							double r = Math.random();
							total_weight *= r;
							for( i=0; i<note_weights.length; i++ ) {
								if( (total_weight -= note_weights[i]) < 0 ) {
									note_no = range.min_note + i;
									break;
								}
							}
							// 決定された音符を追加
							addNote(
								start_tick_pos, tick + min_note_ticks,
								note_no, velocity
							);
							if( random_lyric ) {
								// ランダムな歌詞を追加
								addStringTo(
									0x05,
									VocaloidLyricGenerator.getRandomLyric(),
									start_tick_pos
								);
							}
							prev_note_no = note_no;
						}
						else if( is_bass ) {
							// ベース音を追加
							int note = range.invertedNoteOf(chord.bassNoteSymbol().toNoteNumber());
							addNote(
								start_tick_pos, tick + min_note_ticks,
								note, velocity
							);
						}
						else {
							// コード本体の音を追加
							for( int note : notes ) {
								addNote(
									start_tick_pos, tick + min_note_ticks,
									note, velocity
								);
							}
						}
						is_note_on = false;
					}
				}
			}

		}

	}

	//
	// VOCALOID互換の音素単位で歌詞を生成するクラス
	//
	public static class VocaloidLyricGenerator {
		private static String lyric_elements[] = {
			/*
		      "きゃ","きゅ","きょ",
		      "しゃ","しゅ","しょ",
		      "ちゃ","ちゅ","ちょ",
		      "にゃ","にゅ","にょ",
		      "ひゃ","ひゅ","ひょ",
		      "みゃ","みゅ","みょ",
		      "りゃ","りゅ","りょ",
		      "ぎゃ","ぎゅ","ぎょ",
		      "じゃ","じゅ","じょ",
		      "ぢゃ","ぢゅ","ぢょ",
		      "びゃ","びゅ","びょ",
		      "ぴゃ","ぴゅ","ぴょ",
			 */
			"あ","い","う","え","お",
			"か","き","く","け","こ",
			"さ","し","す","せ","そ",
			"た","ち","つ","て","と",
			"な","に","ぬ","ね","の",
			"は","ひ","ふ","へ","ほ",
			"ま","み","む","め","も",
			"や","ゆ","よ",
			"ら","り","る","れ","ろ",
			"わ","を","ん",
			"が","ぎ","ぐ","げ","ご",
			"ざ","じ","ず","ぜ","ぞ",
			"だ","ぢ","づ","で","ど",
			"ば","び","ぶ","べ","ぼ",
			"ぱ","ぴ","ぷ","ぺ","ぽ",
		};
		//
		// ランダムに音素を返す
		public static String getRandomLyric() {
			return lyric_elements[(int)(Math.random() * lyric_elements.length)];
		}
		/*
    // テキストを音素に分解する
    public static Vector<String> split(String text) {
      Vector<String> sv = new Vector<String>();
      String s, prev_s;
      int i;
      for( i=0; i < text.length(); i++ ) {
        s = text.substring(i,i+1);
        if( "ゃゅょ".indexOf(s) < 0 ) {
          sv.add(s);
        }
        else {
          prev_s = sv.remove(sv.size()-1);
          sv.add( prev_s + s );
        }
      }
      return sv;
    }
		 */
	}

}

