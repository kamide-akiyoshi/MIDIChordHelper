package camidion.chordhelper.music;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import javax.swing.JLabel;

/**
 * 和音（コード - musical chord）のクラス（値は不変）
 */
public class Chord {
	/** コード構成音の順序に対応する色 */
	public static final Color NOTE_INDEX_COLORS[] = {
		Color.red,
		new Color(0x40,0x40,0xFF),
		Color.orange.darker(),
		new Color(0x20,0x99,0x00),
		Color.magenta,
		Color.orange,
		Color.green
	};
	/** 音程差のグループ分け */
	private static enum IntervalGroup {
		THIRD, FIFTH, SEVENTH, NINTH, ELEVENTH, THIRTEENTH
	}
	/** 音程差 */
	public static enum Interval {

		/** 長２度（major 2nd / sus2） */
		SUS2(2, IntervalGroup.THIRD, "sus2", "suspended 2nd"),
		/** 短３度または増２度 */
		MINOR(3, IntervalGroup.THIRD, "m", "minor"),
		/** 長３度 */
		MAJOR(4, IntervalGroup.THIRD, "", "major"),
		/** 完全４度（parfect 4th / sus4） */
		SUS4(5, IntervalGroup.THIRD, "sus4", "suspended 4th"),

		/** 減５度または増４度（トライトーン ＝ 三全音 ＝ 半オクターブ） */
		FLAT5(6, IntervalGroup.FIFTH, "-5", "flatted 5th"),
		/** 完全５度 */
		PARFECT5(7, IntervalGroup.FIFTH, "", "parfect 5th"),
		/** 増５度または短６度 */
		SHARP5(8, IntervalGroup.FIFTH, "+5", "sharped 5th"),

		/** 長６度または減７度 */
		SIXTH(9, IntervalGroup.SEVENTH, "6", "6th"),
		/** 短７度 */
		SEVENTH(10, IntervalGroup.SEVENTH, "7", "7th"),
		/** 長７度 */
		MAJOR_SEVENTH(11, IntervalGroup.SEVENTH, "M7", "major 7th"),

		/** 短９度（短２度の１オクターブ上） */
		FLAT9(13, IntervalGroup.NINTH, "-9", "flatted 9th"),
		/** 長９度（長２度の１オクターブ上） */
		NINTH(14, IntervalGroup.NINTH, "9", "9th"),
		/** 増９度（増２度の１オクターブ上） */
		SHARP9(15, IntervalGroup.NINTH, "+9", "sharped 9th"),

		/** 完全１１度（完全４度の１オクターブ上） */
		ELEVENTH(17, IntervalGroup.ELEVENTH, "11", "11th"),
		/** 増１１度（増４度の１オクターブ上） */
		SHARP11(18, IntervalGroup.ELEVENTH, "+11", "sharped 11th"),

		/** 短１３度（短６度の１オクターブ上） */
		FLAT13(20, IntervalGroup.THIRTEENTH, "-13", "flatted 13th"),
		/** 長１３度（長６度の１オクターブ上） */
		THIRTEENTH(21, IntervalGroup.THIRTEENTH, "13", "13th");

		private Interval(int chromaticOffset, IntervalGroup intervalGroup, String symbol, String description) {
			this.chromaticOffset = chromaticOffset;
			this.intervalGroup = intervalGroup;
			this.symbol = symbol;
			this.description = description;
		}
		/**
		 * 半音差を返します。これはルート音からの音程差を半音単位で表した値です。
		 * @return 半音差
		 */
		public int getChromaticOffset() { return chromaticOffset; }
		private int chromaticOffset;
		/**
		 * この音程が属しているグループを返します。
		 * @return この音程が属しているグループ
		 */
		public IntervalGroup getIntervalGroup() { return intervalGroup; }
		private IntervalGroup intervalGroup;
		/**
		 * コード名に使う略称を返します。
		 * @return 略称
		 */
		public String getSymbol() { return symbol; }
		private String symbol;
		/**
		 * コード用の説明を返します。
		 * @return 説明
		 */
		public String getDescription() { return description; }
		private String description;
	}
	/**
	 * コードネームの音名を除いた部分（サフィックス）を組み立てて返します。
	 * @return コードネームの音名を除いた部分
	 */
	public String symbolSuffix() {
		String suffix = "";
		Interval i3 = intervalMap.get(IntervalGroup.THIRD);
		Interval i5 = intervalMap.get(IntervalGroup.FIFTH);
		Interval i7 = intervalMap.get(IntervalGroup.SEVENTH);
		if( Interval.MINOR == i3 ) suffix += i3.getSymbol();
		if( i7 != null ) suffix += i7.getSymbol();
		if( SUSPENDED.contains(i3) ) suffix += i3.getSymbol();
		if( Interval.PARFECT5 != i5 ) suffix += i5.getSymbol();
		StringJoiner inParen = new StringJoiner(",","(",")").setEmptyValue("");
		for( IntervalGroup group : EXTENDED ) {
			Interval i9 = intervalMap.get(group);
			if( i9 != null ) inParen.add(i9.getSymbol());
		}
		String alias = symbolSuffixAliases.get(suffix += inParen);
		return alias == null ? suffix : alias;
	}
	/**
	 * コードの説明のうち、音名を除いた部分を組み立てて返します。
	 * @return コード説明の音名を除いた部分
	 */
	public String nameSuffix() {
		String suffix = "";
		Interval i3 = intervalMap.get(IntervalGroup.THIRD);
		Interval i5 = intervalMap.get(IntervalGroup.FIFTH);
		Interval i7 = intervalMap.get(IntervalGroup.SEVENTH);
		if( Interval.MINOR == i3 ) suffix += " " + i3.getDescription();
		if( i7 != null ) suffix += " " + i7.getDescription();
		if( SUSPENDED.contains(i3) ) suffix += " " + i3.getDescription();
		if( Interval.PARFECT5 != i5 ) suffix += " " + i5.getDescription();
		StringJoiner inParen = new StringJoiner(",","(",")").setEmptyValue("");
		for( IntervalGroup index : EXTENDED ) {
			Interval i9 = intervalMap.get(index);
			if( i9 != null ) inParen.add(i9.getDescription());
		}
		String alias = nameSuffixAliases.get(suffix += inParen);
		return alias == null ? suffix : alias;
	}
	private static final List<Interval> SUSPENDED = Arrays.asList(
			Interval.SUS2,
			Interval.SUS4);
	private static final List<IntervalGroup> EXTENDED = Arrays.asList(
			IntervalGroup.NINTH,
			IntervalGroup.ELEVENTH,
			IntervalGroup.THIRTEENTH);
	private static final Map<String, String> symbolSuffixAliases = new HashMap<String, String>() {
		{
			put("m-5", "dim");
			put("+5", "aug");
			put("m6-5", "dim7");
			put("(9)", "add9");
			put("7(9)", "9");
			put("M7(9)", "M9");
			put("7+5", "aug7");
			put("m6-5(9)", "dim9");
		}
	};
	private static final Map<String, String> nameSuffixAliases = new HashMap<String, String>() {
		{
			put("", " "+Interval.MAJOR.getDescription());
			put(" minor flatted 5th", " diminished (triad)");
			put(" sharped 5th", " augumented");
			put(" minor 6th flatted 5th", " diminished 7th");
			put("(9th)", " additional 9th");
			put(" 7th(9th)", " 9th");
			put(" major 7th(9th)", " major 9th");
			put(" 7th sharped 5th", " augumented 7th");
			put(" minor 6th flatted 5th(9th)", " diminished 9th");
		}
	};
	private void setSymbolSuffix(String suffix) {
		// ()の中と外に分ける
		String outInParen[] = suffix.split("[\\(\\)]");
		if( outInParen.length == 0 ) return;
		String outParen = suffix;
		String inParen = "";
		if( outInParen.length > 1 ) {
			outParen = outInParen[0];
			inParen = outInParen[1];
		}
		// +5 -5 aug dim
		if( outParen.matches(".*(\\+5|aug|#5).*") ) set(Interval.SHARP5);
		else if( outParen.matches(".*(-5|dim|b5).*") ) set(Interval.FLAT5);
		//
		// 6 7 M7
		if( outParen.matches(".*(M7|maj7|M9|maj9).*") ) set(Interval.MAJOR_SEVENTH);
		else if( outParen.matches(".*(6|dim[79]).*") ) set(Interval.SIXTH);
		else if( outParen.matches(".*7.*") ) set(Interval.SEVENTH);
		//
		// minor sus4  （m と maj7 を間違えないよう比較しつつ、mmaj7 も認識させる）
		if( outParen.matches(".*m.*") && ! outParen.matches(".*ma.*") || outParen.matches(".*mma.*") ) {
			set(Interval.MINOR);
		}
		else if( outParen.matches(".*sus4.*") ) set(Interval.SUS4);
		//
		// extended
		if( outParen.matches(".*9.*") ) {
			set(Interval.NINTH);
			if( ! outParen.matches(".*(add9|6|M9|maj9|dim9).*") ) {
				set(Interval.SEVENTH);
			}
		}
		else for(String p : inParen.split(",")) {
			if( p.matches("(\\+9|#9)") ) set(Interval.SHARP9);
			else if( p.matches("(-9|b9)") ) set(Interval.FLAT9);
			else if( p.matches("9") ) set(Interval.NINTH);

			if( p.matches("(\\+11|#11)") ) set(Interval.SHARP11);
			else if( p.matches("11") ) set(Interval.ELEVENTH);

			if( p.matches("(-13|b13)") ) set(Interval.FLAT13);
			else if( p.matches("13") ) set(Interval.THIRTEENTH);

			// -5 や +5 が () の中にあっても解釈できるようにする
			if( p.matches("(-5|b5)") ) set(Interval.FLAT5);
			else if( p.matches("(\\+5|#5)") ) set(Interval.SHARP5);
		}
	}
	/**
	 * ルート音を返します。
	 * @return ルート音
	 */
	public Note rootNoteSymbol() { return rootNoteSymbol; }
	private Note rootNoteSymbol;
	/**
	 * ベース音を返します。オンコードの場合はルート音と異なります。
	 * @return ベース音
	 */
	public Note bassNoteSymbol() { return bassNoteSymbol; }
	private Note bassNoteSymbol;
	/**
	 * 指定した音程が設定されているか調べます。
	 * @param interval 音程
	 * @return 指定した音程が設定されていたらtrue
	 */
	public boolean isSet(Interval interval) {
		return interval.equals(intervalMap.get(interval.getIntervalGroup()));
	}
	/**
	 * このコードの構成音（ルート音自身やベース音は含まない）を、
	 * ルート音からの音程のコレクション（変更不可能なビュー）として返します。
	 * @return 音程のコレクション
	 */
	public Collection<Interval> intervals() { return intervals; }
	private Collection<Interval> intervals;
	private void fixIntervals() {
		intervals = Collections.unmodifiableCollection(intervalMap.values());
	}
	/** 現在有効な構成音（ルート、ベースは除く）の音程 */
	private Map<IntervalGroup, Interval> intervalMap;
	private void set(Interval interval) {
		intervalMap.put(interval.getIntervalGroup(), interval);
	}
	private void set(Collection<Interval> intervals) {
		for(Interval interval : intervals) if(interval != null) set(interval);
	}

	/**
	 * 指定されたルート音、構成音を持つコードを構築します。
	 * @param root ルート音
	 * @param intervals その他の構成音の音程
	 * （メジャーコードの構成音である長三度・完全五度は、指定しなくてもデフォルトで設定されます）
	 * @throws NullPointerException ルート音にnullが指定された場合
	 */
	public Chord(Note root, Interval... intervals) {
		this(root, root, intervals);
	}
	/**
	 * 指定されたルート音、ベース音、構成音を持つコードを構築します。
	 * @param root ルート音
	 * @param bass ベース音（nullの場合はルート音が指定されたとみなされます）
	 * @param intervals その他の構成音の音程
	 * （メジャーコードの構成音である長三度・完全五度は、指定しなくてもデフォルトで設定されます）
	 * @throws NullPointerException ルート音にnullが指定された場合
	 */
	public Chord(Note root, Note bass, Interval... intervals) {
		this(root, bass, Arrays.asList(intervals));
	}
	/**
	 * 指定されたルート音、ベース音、構成音を持つコードを構築します。
	 * @param root ルート音
	 * @param bass ベース音（nullの場合はルート音が指定されたとみなされます）
	 * @param intervals その他の構成音の音程のコレクション
	 * （メジャーコードの構成音である長三度・完全五度は、指定しなくてもデフォルトで設定されます）
	 * @throws NullPointerException ルート音、または構成音コレクションにnullが指定された場合
	 */
	public Chord(Note root, Note bass, Collection<Interval> intervals) {
		rootNoteSymbol = Objects.requireNonNull(root);
		bassNoteSymbol = (bass==null ? root : bass);
		intervalMap = new HashMap<>();
		set(Interval.MAJOR);
		set(Interval.PARFECT5);
		set(intervals);
		fixIntervals();
	}
	/**
	 * 元のコードのルート音、ベース音以外の構成音の一部を変更した新しいコードを構築します。
	 * @param chord 元のコード
	 * @param intervals 変更したい構成音の音程（ルート音、ベース音を除く）
	 * @throws NullPointerException 元のコードにnullが指定された場合
	 */
	public Chord(Chord original, Interval... intervals) {
		this(original, Arrays.asList(intervals));
	}
	/**
	 * 元のコードのルート音、ベース音以外の構成音の一部を変更した新しいコードを構築します。
	 * @param chord 元のコード
	 * @param intervals 変更したい構成音の音程（ルート音、ベース音を除く）のコレクション
	 * @throws NullPointerException 引数にnullが指定された場合
	 */
	public Chord(Chord original, Collection<Interval> intervals) {
		rootNoteSymbol = original.rootNoteSymbol;
		bassNoteSymbol = original.bassNoteSymbol;
		intervalMap = new HashMap<>(original.intervalMap);
		set(intervals);
		fixIntervals();
	}
	/**
	 * 指定された調と同名のコードを構築します。
	 * @param key 調
	 * @throws NullPointerException 調にnullが指定された場合
	 */
	public Chord(Key key) {
		intervalMap = new HashMap<>(); set(Interval.PARFECT5);
		int keyCo5 = key.toCo5(); if( key.majorMinor() == Key.MajorMinor.MINOR ) {
			keyCo5 += 3; set(Interval.MINOR);
		} else set(Interval.MAJOR);
		bassNoteSymbol = rootNoteSymbol = new Note(keyCo5);
		fixIntervals();
	}
	/**
	 * コード名からコードを構築します。
	 * @param chordSymbol コード名
	 * @throws NullPointerException コード名にnullが指定された場合
	 * @throws IllegalArgumentException コード名が空文字列の場合、または音名で始まっていない場合
	 */
	public Chord(String chordSymbol) {
		String rootOnBass[] = Objects.requireNonNull(chordSymbol, "Chord symbol must not be null").trim().split("(/|on)");
		String root = (rootOnBass.length > 0 ? rootOnBass[0].trim() : "");
		bassNoteSymbol = rootNoteSymbol = new Note(root);
		if( rootOnBass.length > 1 ) {
			String bass = rootOnBass[1].trim();
			if( ! root.equals(bass) ) bassNoteSymbol = new Note(bass);
		}
		intervalMap = new HashMap<>();
		set(Interval.MAJOR);
		set(Interval.PARFECT5);
		setSymbolSuffix(root.replaceFirst("^[A-G][#bx]*",""));
		fixIntervals();
	}
	/**
	 * コードの同一性を判定します。ルート音、ベース音の異名同音は異なるものとみなされます。
	 * @param anObject 比較対象
	 * @return 等しければtrue
	 * @see #equalsEnharmonically(Chord)
	 */
	@Override
	public boolean equals(Object anObject) {
		if( anObject == this ) return true;
		if( anObject instanceof Chord ) {
			Chord another = (Chord) anObject;
			if( ! rootNoteSymbol.equals(another.rootNoteSymbol) ) return false;
			if( ! bassNoteSymbol.equals(another.bassNoteSymbol) ) return false;
			return intervalMap.equals(another.intervalMap);
		}
		return false;
	}
	@Override
	public int hashCode() { return toString().hashCode(); }
	/**
	 * ルート音、ベース音の異名同音を同じとみなしたうえで、コードの同一性を判定します。
	 * @param another 比較対象のコード
	 * @return 等しければtrue
	 * @see #equals(Object)
	 */
	public boolean equalsEnharmonically(Chord another) {
		if( another == this ) return true;
		if( another != null ) {
			if( ! rootNoteSymbol.equalsEnharmonically(another.rootNoteSymbol) ) return false;
			if( ! bassNoteSymbol.equalsEnharmonically(another.bassNoteSymbol) ) return false;
			return intervalMap.equals(another.intervalMap);
		}
		return false;
	}
	/**
	 * コード構成音の数を返します。ルート音は含まれますが、ベース音は含まれません。
	 * @return コード構成音の数
	 */
	public int numberOfNotes() { return intervalMap.size() + 1; }
	/**
	 * 指定された位置にある構成音のノート番号を返します。
	 * @param index 位置（０をルート音とした構成音の順序）
	 * @return ノート番号（該当する音がない場合は -1）
	 */
	public int noteAt(int index) {
		int root = rootNoteSymbol.toNoteNumber();
		if( index == 0 ) return root;
		int current = 0;
		for( IntervalGroup group : IntervalGroup.values() ) {
			Interval interval = intervalMap.get(group);
			if( interval == null ) continue;
			if( ++current == index ) return root + interval.getChromaticOffset();
		}
		return -1;
	}
	/**
	 * コード構成音を格納したノート番号の配列を返します（ベース音は含まれません）。
	 * 音域が指定された場合、その音域の範囲内に収まるように転回されます。
	 * @param range 音域（null可）
	 * @param key キー（null可）
	 * @return ノート番号の配列
	 */
	public int[] toNoteArray(Range range, Key key) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		int ia[] = new int[numberOfNotes()];
		int i;
		ia[i=0] = rootnote;
		Interval itv;
		for( IntervalGroup offsetIndex : IntervalGroup.values() )
			if( (itv = intervalMap.get(offsetIndex)) != null )
				ia[++i] = rootnote + itv.getChromaticOffset();
		if( range != null ) range.invertNotesOf(ia, key);
		return ia;
	}
	/**
	 * MIDIノート番号が、コードの構成音の何番目（０＝ルート音）にあるかを表すインデックス値を返します。
	 * 構成音に該当しない場合は -1 を返します。ベース音は検索されません。
	 * @param noteNumber MIDIノート番号
	 * @return 構成音のインデックス値
	 */
	public int indexOf(int noteNumber) {
		int relativeNote = noteNumber - rootNoteSymbol.toNoteNumber();
		if( Note.mod12(relativeNote) == 0 ) return 0;
		Interval itv;
		int i=0;
		for( IntervalGroup offsetIndex : IntervalGroup.values() ) {
			if( (itv = intervalMap.get(offsetIndex)) != null ) {
				i++;
				if( Note.mod12(relativeNote - itv.getChromaticOffset()) == 0 )
					return i;
			}
		}
		return -1;
	}
	/**
	 * 指定したキーのスケールを外れた構成音がないか調べます。
	 * @param key 調べるキー
	 * @return スケールを外れている構成音がなければtrue
	 * @throws NullPointerException キーにnullが指定された場合
	 */
	public boolean isOnScaleIn(Key key) {
		int root = rootNoteSymbol.toNoteNumber();
		if( ! key.isOnScale(root) ) return false;
		Interval itv;
		for( IntervalGroup offsetIndex : IntervalGroup.values() ) {
			if( (itv = intervalMap.get(offsetIndex)) == null ) continue;
			if( ! key.isOnScale(root + itv.getChromaticOffset()) ) return false;
		}
		return true;
	}
	/**
	 * 指定された調に近いほうの♯、♭の表記で、移調したコードを返します。
	 * @param chromaticOffset 移調幅（半音単位）
	 * @param originalKey 基準とする調
	 * @return 移調した新しいコード（移調幅が０の場合は自分自身）
	 */
	public Chord transposedNewChord(int chromaticOffset, Key originalKey) {
		return transposedNewChord(chromaticOffset, originalKey.toCo5());
	}
	/**
	 * C/Amの調に近いほうの♯、♭の表記で、移調したコードを返します。
	 * @param chromaticOffset 移調幅（半音単位）
	 * @return 移調した新しいコード（移調幅が０の場合は自分自身）
	 */
	public Chord transposedNewChord(int chromaticOffset) {
		return transposedNewChord(chromaticOffset, 0);
	}
	private Chord transposedNewChord(int chromaticOffset, int originalKeyCo5) {
		if( chromaticOffset == 0 ) return this;
		int offsetCo5 = Note.mod12(Note.toggleCo5(chromaticOffset));
		if( offsetCo5 > 6 ) offsetCo5 -= 12;
		int keyCo5 = originalKeyCo5 + offsetCo5;
		int newRootCo5 = rootNoteSymbol.toCo5() + offsetCo5;
		int newBassCo5 = bassNoteSymbol.toCo5() + offsetCo5;
		if( keyCo5 > 6 ) {
			newRootCo5 -= 12;
			newBassCo5 -= 12;
		}
		else if( keyCo5 < -5 ) {
			newRootCo5 += 12;
			newBassCo5 += 12;
		}
		Note root = new Note(newRootCo5);
		Note bass = (newBassCo5 == newRootCo5 ? root : new Note(newBassCo5));
		return new Chord(root, bass, intervals);
	}

	/** この和音の文字列表現としてコード名（例： C、Am、G7）を返します。 */
	@Override
	public String toString() {
		String chordSymbol = rootNoteSymbol + symbolSuffix();
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) chordSymbol += "/" + bassNoteSymbol;
		return chordSymbol;
	}
	/**
	 * コード名を HTML で返します。
	 *
	 * Swing の {@link JLabel#setText(String)} は HTML で指定できるので、
	 * 文字の大きさに変化をつけることができます。
	 *
	 * @param colorName 色のHTML表現（色名または #RRGGBB 形式）
	 * @return コード名のHTML
	 */
	public String toHtmlString(String colorName) {
		String smallSpan = "<span style=\"font-size: 120%\">";
		String endSpan = "</span>";
		String root = rootNoteSymbol.toString();
		String formattedRoot = (root.length() == 1) ? root + smallSpan :
			root.replace("#",smallSpan+"<sup>#</sup>").
			replace("b",smallSpan+"<sup>b</sup>").
			replace("x",smallSpan+"<sup>x</sup>");
		String formattedBass = "";
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			String bass = bassNoteSymbol.toString();
			formattedBass = (bass.length() == 1) ? bass + smallSpan :
				bass.replace("#",smallSpan+"<sup>#</sup>").
				replace("b",smallSpan+"<sup>b</sup>").
				replace("x",smallSpan+"<sup>x</sup>");
			formattedBass = "/" + formattedBass + endSpan;
		}
		String suffix = symbolSuffix().
			replace("-5","<sup>-5</sup>").
			replace("+5","<sup>+5</sup>");
		return
			"<html>" +
			"<span style=\"color: " + colorName + "; font-size: 170% ; white-space: nowrap ;\">" +
			formattedRoot + suffix + endSpan + formattedBass +
			"</span>" +
			"</html>" ;
	}
	/**
	 * コードの説明（英語）を返します。
	 * @return コードの説明（英語）
	 */
	public String toName() {
		String name = rootNoteSymbol.toStringIn(Note.Language.NAME) + nameSuffix() ;
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			name += " on " + bassNoteSymbol.toStringIn(Note.Language.NAME);
		}
		return name;
	}
}