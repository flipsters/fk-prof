import React from "react";

const Header = props => (
  <div className="mdl-layout mdl-js-layout mdl-layout--fixed-header" >
    <header className="mdl-layout__header">
      <div className="mdl-layout__header-row" style={{backgroundColor: props.color}}>
        <span className="mdl-layout-title">Flipkart Profiler</span>
        <div className="mdl-layout-spacer" />
        <a href={`/profiler/settings`}>
          <button className="mdl-button mdl-js-button mdl-button--icon" style={{color: "white"}}>
            <span className="material-icons">settings</span>
          </button>
        </a>
      </div>
    </header>
  </div>
  );

export default Header;
