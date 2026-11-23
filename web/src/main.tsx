import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes } from "react-router";

import "./index.css";
import { AppLayout } from "./pages/AppLayout";
import { HistoryPage } from "./pages/HistoryPage";
import { LinkGeneratorPage } from "./pages/LinkGeneratorPage";
import { NewSearchPage } from "./pages/NewSearchPage";
import { SearchPage } from "./pages/SearchPage";
import { StoresPage } from "./pages/StoresPage";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<NewSearchPage />} />
          <Route path="buscas" element={<HistoryPage />} />
          <Route path="buscas/:id" element={<SearchPage />} />
          <Route path="gerar-link" element={<LinkGeneratorPage />} />
          <Route path="lojas" element={<StoresPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
