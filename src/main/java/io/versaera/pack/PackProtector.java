package io.versaera.pack;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * 리소스팩 zip 을 "게임은 읽고, 압축 프로그램은 못 여는" 모양으로 쓴다 (RP-02).
 * <p>
 * 마인크래프트 클라이언트는 팩을 {@code java.util.zip.ZipFile} 로 읽는다. ZipFile 은 끝의 <b>중앙 디렉터리</b>로 이름 · 크기 · 위치를 찾고,
 * 각 파일 앞의 <b>로컬 헤더</b>에서는 길이만 보고 건너뛰며, CRC 도 검사하지 않는다. 그래서:
 * <ul>
 *   <li>로컬 헤더의 이름을 같은 길이의 무의미한 글자로 바꾸고, 크기 · CRC 를 0 으로 둔다 → 앞에서부터 읽는 도구(스트림 해제 · 탐색기 · 대부분의 압축 프로그램)는 이름이 어긋나거나 빈 파일로 본다</li>
 *   <li>중앙 디렉터리의 CRC 를 0 으로 둔다 → 검사하는 도구(7-Zip · 반디집 · unzip · 파이썬)는 모든 파일을 손상으로 본다</li>
 *   <li>시각은 0 (결정적 바이트 — 같은 콘텐츠면 같은 SHA-1)</li>
 * </ul>
 * 한계: 게임이 읽을 수 있는 이상 직접 프로그램을 짜서 ZipFile 로 읽으면 열린다. 흔한 도구로 "그냥 열어 보는 것"을 막는 장치다.
 */
public final class PackProtector {
    private PackProtector() {
    }

    private record Central(byte[] name, int csize, int usize, int offset, boolean dir) {}

    /** files: 경로 → 내용 (넣은 순서대로). 폴더 항목은 넣지 않는다 (ZipFile 은 필요 없다) */
    public static byte[] write(Map<String, byte[]> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Central> cen = new ArrayList<>();
        Deflater def = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            for (Map.Entry<String, byte[]> f : files.entrySet()) {
                byte[] name = f.getKey().getBytes(StandardCharsets.UTF_8);
                byte[] data = deflate(def, f.getValue());
                int offset = out.size();
                // 로컬 헤더: 이름은 같은 길이의 잡음, 크기 · CRC 0 (ZipFile 은 이름 길이로 건너뛰기만 한다)
                le32(out, 0x04034b50);
                le16(out, 20);
                le16(out, 0);          // 플래그
                le16(out, 8);          // deflate
                le32(out, 0);          // 시각
                le32(out, 0);          // CRC
                le32(out, 0);          // 압축 크기
                le32(out, 0);          // 원래 크기
                le16(out, name.length);
                le16(out, 0);
                out.writeBytes(noise(name, offset));
                out.writeBytes(data);
                cen.add(new Central(name, data.length, f.getValue().length, offset, false));
            }
            int cenStart = out.size();
            for (Central c : cen) {
                le32(out, 0x02014b50);
                le16(out, 20);         // 만든 버전
                le16(out, 20);         // 필요한 버전
                le16(out, 0);
                le16(out, 8);
                le32(out, 0);          // 시각
                le32(out, 0);          // CRC — 0 (게임은 검사하지 않음)
                le32(out, c.csize());
                le32(out, c.usize());
                le16(out, c.name().length);
                le16(out, 0);          // extra
                le16(out, 0);          // 주석
                le16(out, 0);          // 디스크
                le16(out, 0);          // 내부 속성
                le32(out, 0);          // 외부 속성
                le32(out, c.offset());
                out.writeBytes(c.name());
            }
            int cenSize = out.size() - cenStart;
            le32(out, 0x06054b50);
            le16(out, 0);
            le16(out, 0);
            le16(out, cen.size());
            le16(out, cen.size());
            le32(out, cenSize);
            le32(out, cenStart);
            le16(out, 0);
            return out.toByteArray();
        } finally {
            def.end();
        }
    }

    private static byte[] deflate(Deflater def, byte[] in) {
        def.reset();
        def.setInput(in);
        def.finish();
        ByteArrayOutputStream o = new ByteArrayOutputStream(Math.max(64, in.length / 2));
        byte[] buf = new byte[8192];
        while (!def.finished()) {
            int n = def.deflate(buf);
            o.write(buf, 0, n);
        }
        return o.toByteArray();
    }

    /** 같은 길이의 이름 잡음 — 위치로 정해지므로 결정적 */
    private static byte[] noise(byte[] name, int seed) {
        byte[] out = new byte[name.length];
        long x = 0x9E3779B97F4A7C15L ^ seed;
        String abc = "abcdefghijklmnopqrstuvwxyz0123456789_";
        for (int i = 0; i < out.length; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            out[i] = (byte) abc.charAt((int) Math.floorMod(x, abc.length()));
        }
        return out;
    }

    private static void le16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff);
        o.write((v >>> 8) & 0xff);
    }

    private static void le32(ByteArrayOutputStream o, int v) {
        le16(o, v & 0xffff);
        le16(o, (v >>> 16) & 0xffff);
    }
}
