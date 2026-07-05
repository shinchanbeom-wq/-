#include <opencv2/opencv.hpp>

#include <algorithm>
#include <cmath>
#include <iostream>
#include <string>
#include <vector>

using namespace cv;
using namespace std;

// 조명 정규화: 배경 밝기 추정 후 나눠서 그림자/불균일 조명 제거
Mat illuminationNormalize(const Mat& gray) {
    Mat background;
    int k = max(31, (gray.cols / 8) | 1);
    GaussianBlur(gray, background, Size(k, k), 0);

    Mat gray_f, bg_f, norm_f;
    gray.convertTo(gray_f, CV_32F);
    background.convertTo(bg_f, CV_32F);
    divide(gray_f, bg_f + 1.0, norm_f, 255.0);

    Mat result;
    normalize(norm_f, result, 0, 255, NORM_MINMAX);
    result.convertTo(result, CV_8U);
    return result;
}

Mat gammaCorrect(const Mat& gray, double gamma) {
    Mat lut(1, 256, CV_8U);
    uchar* p = lut.ptr();
    for (int i = 0; i < 256; i++) {
        p[i] = saturate_cast<uchar>(pow(i / 255.0, gamma) * 255.0);
    }
    Mat result;
    LUT(gray, lut, result);
    return result;
}

// 목표 평균 밝기(targetMean)에 가장 가까워지는 감마값을 이분탐색으로 자동 결정
double autoGamma(const Mat& gray, double targetMean = 128.0) {
    double lo = 0.2, hi = 3.0;
    double bestGamma = 1.0, bestDiff = 1e9;

    for (int iter = 0; iter < 20; iter++) {
        double mid = (lo + hi) / 2.0;
        Mat corrected = gammaCorrect(gray, mid);
        double m = mean(corrected)[0];
        double diff = fabs(m - targetMean);

        if (diff < bestDiff) {
            bestDiff = diff;
            bestGamma = mid;
        }

        if (m < targetMean) {
            hi = mid; // 너무 어두우면 gamma를 더 낮춰야(밝아지도록) -> hi 축소
        } else {
            lo = mid;
        }
    }
    return bestGamma;
}

// CLAHE clipLimit 후보들 중, 결과의 표준편차(대비)가 목표범위에 가장 가까운 값 선택
double autoClahe(const Mat& gray, Mat& outResult, double targetStd = 55.0) {
    vector<double> candidates = {1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0, 6.0};
    double bestClip = 2.0, bestDiff = 1e9;
    Mat bestOut;

    for (double clip : candidates) {
        Ptr<CLAHE> clahe = createCLAHE(clip, Size(8, 8));
        Mat out;
        clahe->apply(gray, out);

        Scalar meanVal, stddevVal;
        meanStdDev(out, meanVal, stddevVal);
        double diff = fabs(stddevVal[0] - targetStd);

        if (diff < bestDiff) {
            bestDiff = diff;
            bestClip = clip;
            bestOut = out;
        }
    }
    outResult = bestOut;
    return bestClip;
}

int main(int argc, char** argv) {
    string inputPath;

    if (argc >= 2) {
        // exe에 이미지를 드래그 앤 드롭하거나 "program.exe 사진경로.jpg" 로 실행한 경우
        inputPath = argv[1];
    } else {
        // 인자가 없으면 콘솔에서 직접 입력받기
        cout << "===================================" << endl;
        cout << " 이미지 보정 프로그램 (감마 + CLAHE 자동 최적화)" << endl;
        cout << "===================================" << endl;
        cout << "이미지 파일 경로를 입력하세요 (또는 exe에 이미지를 드래그하세요): ";
        getline(cin, inputPath);
    }

    // 따옴표로 감싸져서 들어온 경로 처리 (드래그앤드롭 시 종종 발생)
    if (!inputPath.empty() && inputPath.front() == '"' && inputPath.back() == '"') {
        inputPath = inputPath.substr(1, inputPath.size() - 2);
    }

    Mat img_color = imread(inputPath, IMREAD_COLOR);
    if (img_color.empty()) {
        cout << "이미지를 불러올 수 없습니다: " << inputPath << endl;
        cout << "아무 키나 누르면 종료됩니다..." << endl;
        cin.get();
        return -1;
    }

    resize(img_color, img_color, Size(800, 800), 0, 0, INTER_CUBIC);

    Mat img_gray_raw;
    cvtColor(img_color, img_gray_raw, COLOR_BGR2GRAY);

    // 1. 조명 정규화 (어두운 영역 정보손실의 근본 원인 제거)
    Mat img_illum = illuminationNormalize(img_gray_raw);

    // 2. 목표 평균 밝기에 맞춘 감마 자동 탐색
    double gamma = autoGamma(img_illum, 128.0);
    Mat img_gamma = gammaCorrect(img_illum, gamma);
    cout << "자동 탐색된 감마값: " << gamma << endl;

    // 3. CLAHE clipLimit 자동 탐색
    Mat img_final_gray;
    double bestClip = autoClahe(img_gamma, img_final_gray, 55.0);
    cout << "자동 탐색된 CLAHE clipLimit: " << bestClip << endl;

    // 4. 컬러 버전도 생성: 원본 컬러의 밝기(L 채널)만 위 결과로 교체
    Mat lab, img_final_color;
    cvtColor(img_color, lab, COLOR_BGR2Lab);
    vector<Mat> lab_planes(3);
    split(lab, lab_planes);
    img_final_gray.copyTo(lab_planes[0]);
    merge(lab_planes, lab);
    cvtColor(lab, img_final_color, COLOR_Lab2BGR);

    // 파일명에서 확장자 제거해서 결과 파일명 생성 (원본과 같은 폴더에 저장)
    size_t lastDot = inputPath.find_last_of('.');
    string baseName = (lastDot == string::npos) ? inputPath : inputPath.substr(0, lastDot);
    string grayPath = baseName + "_result_gray.jpg";
    string colorPath = baseName + "_result_color.jpg";

    imwrite(grayPath, img_final_gray);
    imwrite(colorPath, img_final_color);

    imshow("최종 결과 (그레이, 감마+CLAHE 최적화)", img_final_gray);
    imshow("최종 결과 (컬러)", img_final_color);

    cout << "\n'" << grayPath << "'" << endl;
    cout << "'" << colorPath << "' 로 저장되었습니다." << endl;
    cout << "ESC 키를 누르면 종료됩니다." << endl;

    while (waitKey(30) != 27) {}

    destroyAllWindows();
    return 0;
}
